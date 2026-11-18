package ma.youcode.covoiturage.service;

import jakarta.persistence.EntityManager;
import ma.youcode.covoiturage.dto.reservation.*;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReservationConcurrencyTest {
    @Autowired private ReservationService service;
    @Autowired private EntityManager entityManager;
    @Autowired private TrajetRepository trajetRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;
    private Fixture fixture;

    private record Fixture(Long conducteurId, Long vehiculeId, Long trajetId,
                           Long firstPassengerId, Long secondPassengerId, Long monteeId, Long descenteId) {}

    @BeforeEach void prepareCommittedData() {
        tx = new TransactionTemplate(transactionManager);
        fixture = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Conducteur c = new Conducteur();
            c.setFirstName("Test"); c.setLastName("Concurrence"); c.setPhone("0600000000");
            c.setEmail(suffix + "-c@example.com"); c.setLicenseNumber(suffix);
            entityManager.persist(c);
            Passager first = passenger(suffix + "-1@example.com");
            Passager second = passenger(suffix + "-2@example.com");
            Vehicule v = new Vehicule();
            v.setBrand("Dacia"); v.setModel("Logan"); v.setColor("Blanc");
            v.setCapacity(4); v.setRegistrationNumber(suffix); v.setConducteur(c);
            entityManager.persist(v);
            Trajet t = new Trajet();
            t.setConducteur(c); t.setVehicule(v); t.setOfferedSeats(1);
            t.setDepartureCity("Rabat"); t.setArrivalCity("Marrakech");
            t.setDepartureTime(LocalDateTime.now().plusDays(3));
            PointArret montee = point("Rabat", 0, t.getDepartureTime(), "0.00");
            PointArret descente = point("Marrakech", 1, t.getDepartureTime().plusHours(4), "120.00");
            t.addPointArret(montee); t.addPointArret(descente);
            entityManager.persist(t);
            entityManager.flush();
            return new Fixture(c.getId(), v.getId(), t.getId(), first.getId(), second.getId(), montee.getId(), descente.getId());
        });
    }

    @AfterEach void removeOnlyOwnData() {
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Reservation r where r.trajet.id = :id")
                    .setParameter("id", fixture.trajetId()).executeUpdate();
            trajetRepository.deleteById(fixture.trajetId());
            entityManager.flush();
            entityManager.remove(entityManager.find(Vehicule.class, fixture.vehiculeId()));
            entityManager.flush();
            entityManager.remove(entityManager.find(Conducteur.class, fixture.conducteurId()));
            entityManager.remove(entityManager.find(Passager.class, fixture.firstPassengerId()));
            entityManager.remove(entityManager.find(Passager.class, fixture.secondPassengerId()));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serializesLastSeatAndReleasesLockAfterCommitOrRollback(boolean rollbackFirst) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstReserved = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        try {
            Future<ReservationResponse> first = workers.submit(() -> tx.execute(status -> {
                firstPid.set(backendPid());
                ReservationResponse response = reserve(fixture.firstPassengerId());
                firstReserved.countDown();
                await(releaseFirst);
                if (rollbackFirst) status.setRollbackOnly();
                return response;
            }));
            assertThat(firstReserved.await(15, TimeUnit.SECONDS)).isTrue();

            Future<ReservationResponse> second = workers.submit(() -> {
                try {
                    return tx.execute(status -> {
                        secondPid.set(backendPid());
                        secondStarted.countDown();
                        return reserve(fixture.secondPassengerId());
                    });
                } catch (BusinessException refused) {
                    assertThat(refused).hasMessage("Seul un trajet PUBLIE peut etre reserve");
                    return null;
                }
            });
            assertThat(secondStarted.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(secondPid.get()).isNotEqualTo(firstPid.get());

            // Observation reelle dans PostgreSQL : le second attend le verrou du premier.
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!blocked && System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(tx.execute(status -> entityManager.createNativeQuery(
                        "select :firstPid = any(pg_blocking_pids(:secondPid))", Boolean.class)
                        .setParameter("firstPid", firstPid.get()).setParameter("secondPid", secondPid.get())
                        .getSingleResult()));
                if (!blocked) Thread.sleep(25);
            }
            assertThat(blocked).as("La seconde transaction attend reellement le verrou PostgreSQL").isTrue();
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();

            ReservationResponse firstResponse = first.get(15, TimeUnit.SECONDS);
            ReservationResponse secondResponse = second.get(15, TimeUnit.SECONDS);
            if (rollbackFirst) assertThat(secondResponse).isNotNull();
            else assertThat(secondResponse).isNull();

            tx.executeWithoutResult(status -> {
                Long count = entityManager.createQuery(
                        "select count(r) from Reservation r where r.trajet.id = :id", Long.class)
                        .setParameter("id", fixture.trajetId()).getSingleResult();
                assertThat(count).isEqualTo(1);
                assertThat(reservationRepository.existsById(firstResponse.id())).isEqualTo(!rollbackFirst);
                assertThat(reservationRepository.sumPlacesByTrajetIdAndStatut(
                        fixture.trajetId(), StatutReservation.CONFIRMEE)).isEqualTo(1);
                assertThat(trajetRepository.findById(fixture.trajetId()).orElseThrow().getStatus())
                        .isEqualTo(StatutTrajet.COMPLET);
            });
            assertThat(service.getRemainingSeats(fixture.trajetId())).isZero();
        } finally {
            releaseFirst.countDown();
            workers.shutdown();
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                workers.shutdownNow();
                throw new AssertionError("Les transactions de test ne se sont pas terminees");
            }
        }
    }

    private ReservationResponse reserve(Long passagerId) {
        return service.create(passagerId, passagerId,
                new ReservationCreateRequest(fixture.trajetId(), fixture.monteeId(), fixture.descenteId(), 1));
    }
    private int backendPid() {
        return ((Number) entityManager.createNativeQuery("select pg_backend_pid()").getSingleResult()).intValue();
    }
    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Delai de synchronisation depasse");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
    private Passager passenger(String email) {
        Passager p = new Passager(); p.setFirstName("Test"); p.setLastName("Concurrence");
        p.setEmail(email); p.setPhone("0600000000"); entityManager.persist(p); return p;
    }
    private PointArret point(String city, int order, LocalDateTime time, String price) {
        PointArret p = new PointArret(); p.setCity(city); p.setStopOrder(order);
        p.setPassageTime(time); p.setPriceFromDeparture(new BigDecimal(price)); return p;
    }
}
