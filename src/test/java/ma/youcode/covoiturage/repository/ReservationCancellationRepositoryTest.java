package ma.youcode.covoiturage.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.service.ReservationService;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ReservationCancellationRepositoryTest {
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private TrajetRepository trajetRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;
    private Fixture fixture;
    private enum Contender { READ_CANCELLATION, BOOKING, CANCEL }
    private record Fixture(Long conducteurId, Long vehiculeId, Long passagerId, Long otherPassagerId,
                           Long trajetId, Long monteeId, Long descenteId, Long reservationId) {}

    @BeforeEach void prepareCommittedFixture() {
        tx = new TransactionTemplate(transactionManager);
        fixture = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Conducteur c = new Conducteur(); c.setFirstName("Test"); c.setLastName("Annulation");
            c.setEmail(suffix + "@example.com"); c.setPhone("0600000000"); c.setLicenseNumber(suffix);
            entityManager.persist(c);
            Vehicule v = new Vehicule(); v.setBrand("Dacia"); v.setModel("Logan"); v.setColor("Blanc");
            v.setRegistrationNumber(suffix); v.setCapacity(4); v.setConducteur(c); entityManager.persist(v);
            Passager p = passenger(); Passager other = passenger();
            LocalDateTime departure = LocalDateTime.now().plusDays(3);
            Trajet t = new Trajet(); t.setConducteur(c); t.setVehicule(v); t.setOfferedSeats(1);
            t.setDepartureCity("Rabat"); t.setArrivalCity("Marrakech"); t.setDepartureTime(departure);
            t.setStatus(StatutTrajet.COMPLET);
            PointArret montee = point("Rabat", 0, departure, "0.00");
            PointArret descente = point("Marrakech", 1, departure.plusHours(4), "120.00");
            t.addPointArret(montee); t.addPointArret(descente); entityManager.persist(t);
            Reservation r = new Reservation(); r.setPassager(p); r.setTrajet(t);
            r.setMontee(montee); r.setDescente(descente); r.setNombrePlaces(1);
            r.setMontantTotal(new BigDecimal("120.00")); r.setDateReservation(LocalDateTime.now());
            r.setStatut(StatutReservation.CONFIRMEE); entityManager.persist(r); entityManager.flush();
            return new Fixture(c.getId(), v.getId(), p.getId(), other.getId(), t.getId(),
                    montee.getId(), descente.getId(), r.getId());
        });
    }

    @AfterEach void cleanOnlyOwnFixture() {
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Reservation r where r.trajet.id = :id")
                    .setParameter("id", fixture.trajetId()).executeUpdate();
            trajetRepository.deleteById(fixture.trajetId()); entityManager.flush();
            entityManager.remove(entityManager.find(Vehicule.class, fixture.vehiculeId())); entityManager.flush();
            entityManager.remove(entityManager.find(Conducteur.class, fixture.conducteurId()));
            entityManager.remove(entityManager.find(Passager.class, fixture.passagerId()));
            entityManager.remove(entityManager.find(Passager.class, fixture.otherPassagerId()));
        });
    }

    @Test void readsScalarIdWithoutLoadingEntitiesThenLocksInTripReservationOrder() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
        tx.executeWithoutResult(status -> {
            Long trajetId = reservationRepository.findTrajetIdByReservationId(fixture.reservationId()).orElseThrow();
            assertThat(trajetId).isEqualTo(fixture.trajetId());
            assertThat(statistics.getEntityLoadCount()).isZero();
            Trajet t = trajetRepository.findByIdForReservation(trajetId).orElseThrow();
            Reservation r = reservationRepository.findByIdForCancellation(fixture.reservationId(), trajetId).orElseThrow();
            assertThat(r.getTrajet().getId()).isEqualTo(t.getId());
            assertThat(r.getPassager().getId()).isEqualTo(fixture.passagerId());
            assertThat(r.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
            assertThat(r.getNombrePlaces()).isEqualTo(1);
            assertThat(r.getMontantTotal()).isEqualByComparingTo("120.00");
        });
    }

    @Test void returnsEmptyForMissingReservationOrWrongTrip() {
        tx.executeWithoutResult(status -> {
            assertThat(reservationRepository.findTrajetIdByReservationId(-1L)).isEmpty();
            trajetRepository.findByIdForReservation(fixture.trajetId()).orElseThrow();
            assertThat(reservationRepository.findByIdForCancellation(-1L, fixture.trajetId())).isEmpty();
            assertThat(reservationRepository.findByIdForCancellation(fixture.reservationId(), -1L)).isEmpty();
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void waitingCancellationReadsLatestStateAfterCommitOrRollback(boolean rollbackFirst) throws Exception {
        runContenders(rollbackFirst, Contender.READ_CANCELLATION);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bookingUsesSameTripLockAndWaitsForCancellationCommitOrRollback(boolean rollbackFirst) throws Exception {
        runContenders(rollbackFirst, Contender.BOOKING);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void twoActualCancellationsNeverReleaseTheSamePlaceTwice(boolean rollbackFirst) throws Exception {
        runContenders(rollbackFirst, Contender.CANCEL);
    }

    @Test void cancellationPersistsReopensTripAndKeepsHistoryAndRejectsRepeat() {
        reservationService.cancel(fixture.passagerId(), fixture.reservationId(), fixture.passagerId());
        tx.executeWithoutResult(status -> {
            Reservation r = reservationRepository.findById(fixture.reservationId()).orElseThrow();
            assertThat(r.getStatut()).isEqualTo(StatutReservation.ANNULEE);
            assertThat(r.getNombrePlaces()).isEqualTo(1);
            assertThat(r.getMontantTotal()).isEqualByComparingTo("120.00");
            assertThat(r.getDateReservation()).isNotNull();
            assertThat(trajetRepository.findById(fixture.trajetId()).orElseThrow().getStatus()).isEqualTo(StatutTrajet.PUBLIE);
        });
        assertThat(reservationService.getRemainingSeats(fixture.trajetId())).isEqualTo(1);
        assertThat(reservationService.getByPassager(fixture.passagerId(), fixture.passagerId()))
                .singleElement().satisfies(r -> assertThat(r.statut()).isEqualTo(StatutReservation.ANNULEE));
        assertThatThrownBy(() -> reservationService.cancel(fixture.passagerId(), fixture.reservationId(), fixture.passagerId()))
                .isInstanceOf(BusinessException.class).hasMessage("Cette reservation est deja annulee");
        assertThat(reservationService.getRemainingSeats(fixture.trajetId())).isEqualTo(1);
    }

    @Test void rollsBackReservationAndTripReopeningTogether() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            reservationService.cancel(fixture.passagerId(), fixture.reservationId(), fixture.passagerId());
            entityManager.flush();
            throw new IllegalStateException("Rollback volontaire apres annulation");
        })).isInstanceOf(IllegalStateException.class);
        assertUnchanged();
    }

    @Test void refusesLateCancellationWithoutChangingDatabase() {
        tx.executeWithoutResult(status -> trajetRepository.findById(fixture.trajetId()).orElseThrow()
                .setDepartureTime(LocalDateTime.now().plusHours(2)));
        assertThatThrownBy(() -> reservationService.cancel(fixture.passagerId(), fixture.reservationId(), fixture.passagerId()))
                .isInstanceOf(BusinessException.class);
        assertUnchanged();
    }

    @Test void refusesOtherPassengerWithoutChangingDatabase() {
        assertThatThrownBy(() -> reservationService.cancel(fixture.otherPassagerId(), fixture.reservationId(), fixture.otherPassagerId()))
                .isInstanceOf(ma.youcode.covoiturage.exception.ForbiddenException.class);
        assertUnchanged();
    }

    private void assertUnchanged() {
        tx.executeWithoutResult(status -> {
            assertThat(reservationRepository.findById(fixture.reservationId()).orElseThrow().getStatut())
                    .isEqualTo(StatutReservation.CONFIRMEE);
            assertThat(trajetRepository.findById(fixture.trajetId()).orElseThrow().getStatus()).isEqualTo(StatutTrajet.COMPLET);
        });
        assertThat(reservationService.getRemainingSeats(fixture.trajetId())).isZero();
    }

    private void runContenders(boolean rollbackFirst, Contender contender) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstChanged = new CountDownLatch(1), releaseFirst = new CountDownLatch(1), secondStarted = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger(), secondPid = new AtomicInteger();
        try {
            Future<?> first = workers.submit(() -> tx.executeWithoutResult(status -> {
                firstPid.set(backendPid());
                reservationService.cancel(fixture.passagerId(), fixture.reservationId(), fixture.passagerId());
                entityManager.flush(); firstChanged.countDown(); await(releaseFirst);
                if (rollbackFirst) status.setRollbackOnly();
            }));
            assertThat(firstChanged.await(15, TimeUnit.SECONDS)).isTrue();
            Future<String> second = workers.submit(() -> {
                try {
                    return tx.execute(status -> {
                        secondPid.set(backendPid());
                        if (contender == Contender.BOOKING) {
                            secondStarted.countDown();
                            reservationService.create(fixture.otherPassagerId(), fixture.otherPassagerId(),
                                    new ReservationCreateRequest(fixture.trajetId(), fixture.monteeId(), fixture.descenteId(), 1));
                            return "RESERVEE";
                        }
                        if (contender == Contender.CANCEL) {
                            secondStarted.countDown();
                            reservationService.cancel(fixture.passagerId(), fixture.reservationId(), fixture.passagerId());
                            return "ANNULEE";
                        }
                        Long tripId = reservationRepository.findTrajetIdByReservationId(fixture.reservationId()).orElseThrow();
                        secondStarted.countDown();
                        trajetRepository.findByIdForReservation(tripId).orElseThrow();
                        return reservationRepository.findByIdForCancellation(fixture.reservationId(), tripId)
                                .orElseThrow().getStatut().name();
                    });
                } catch (BusinessException e) {
                    assertThat(contender).isNotEqualTo(Contender.READ_CANCELLATION);
                    assertThat(e).hasMessage(contender == Contender.BOOKING
                            ? "Seul un trajet PUBLIE peut etre reserve" : "Cette reservation est deja annulee");
                    return "REFUSEE";
                }
            });
            assertThat(secondStarted.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(secondPid.get()).isNotEqualTo(firstPid.get());
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!blocked && System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(tx.execute(status -> entityManager.createNativeQuery(
                        "select :firstPid = any(pg_blocking_pids(:secondPid))", Boolean.class)
                        .setParameter("firstPid", firstPid.get()).setParameter("secondPid", secondPid.get()).getSingleResult()));
                if (!blocked) Thread.sleep(25);
            }
            assertThat(blocked).as("Le second attend reellement le verrou PostgreSQL du trajet").isTrue();
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown(); first.get(15, TimeUnit.SECONDS);
            String result = second.get(15, TimeUnit.SECONDS);
            String expected = switch (contender) {
                case BOOKING -> rollbackFirst ? "REFUSEE" : "RESERVEE";
                case CANCEL -> rollbackFirst ? "ANNULEE" : "REFUSEE";
                case READ_CANCELLATION -> rollbackFirst ? "CONFIRMEE" : "ANNULEE";
            };
            assertThat(result).isEqualTo(expected);
            tx.executeWithoutResult(status -> {
                var original = reservationRepository.findById(fixture.reservationId()).orElseThrow();
                assertThat(original.getStatut()).isEqualTo(rollbackFirst && contender != Contender.CANCEL
                        ? StatutReservation.CONFIRMEE : StatutReservation.ANNULEE);
                long confirmedSeats = reservationRepository.sumPlacesByTrajetIdAndStatut(fixture.trajetId(), StatutReservation.CONFIRMEE);
                boolean full = contender == Contender.BOOKING || (rollbackFirst && contender == Contender.READ_CANCELLATION);
                assertThat(confirmedSeats).isEqualTo(full ? 1L : 0L);
                assertThat(trajetRepository.findById(fixture.trajetId()).orElseThrow().getStatus())
                        .isEqualTo(full ? StatutTrajet.COMPLET : StatutTrajet.PUBLIE);
            });
        } finally {
            releaseFirst.countDown(); workers.shutdown();
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                workers.shutdownNow(); throw new AssertionError("Transactions de test non terminees");
            }
        }
    }
    private int backendPid() {
        return ((Number) entityManager.createNativeQuery("select pg_backend_pid()").getSingleResult()).intValue();
    }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Delai de synchronisation depasse"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    private Passager passenger() {
        Passager p = new Passager(); p.setFirstName("Test"); p.setLastName("Annulation");
        p.setPhone("0600000000"); p.setEmail(UUID.randomUUID() + "@example.com"); entityManager.persist(p); return p;
    }
    private PointArret point(String city, int order, LocalDateTime time, String price) {
        PointArret p = new PointArret(); p.setCity(city); p.setStopOrder(order);
        p.setPassageTime(time); p.setPriceFromDeparture(new BigDecimal(price)); return p;
    }
}
