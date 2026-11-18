package ma.youcode.covoiturage.service;

import jakarta.persistence.EntityManager;
import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class TrajetCancellationIntegrationTest {
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TrajetService trajetService;
    @Autowired private ReservationService reservationService;
    @Autowired private TrajetRepository trajetRepository;
    @Autowired private ReservationRepository reservationRepository;
    private TransactionTemplate tx;
    private Fixture fixture;
    private record Fixture(Long conducteurId, Long vehiculeId, List<Long> passagers,
                           Long trajetId, Long otherTrajetId, List<Long> reservations,
                           Long otherReservationId, Long monteeId, Long descenteId) {}

    @BeforeEach void prepareCommittedFixture() {
        tx = new TransactionTemplate(transactionManager);
        fixture = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Conducteur c = new Conducteur(); c.setFirstName("Test"); c.setLastName("Annulation trajet");
            c.setPhone("0600000000"); c.setEmail(suffix + "@example.com"); c.setLicenseNumber(suffix);
            entityManager.persist(c);
            Vehicule v = new Vehicule(); v.setBrand("Dacia"); v.setModel("Logan"); v.setColor("Blanc");
            v.setCapacity(4); v.setRegistrationNumber(suffix); v.setConducteur(c); entityManager.persist(v);
            List<Passager> passengers = List.of(passenger(), passenger(), passenger());
            Trajet t = trip(c, v); t.setStatus(StatutTrajet.COMPLET);
            Trajet other = trip(c, v);
            Reservation old = reservation(t, passengers.get(0), 1, StatutReservation.ANNULEE);
            Reservation first = reservation(t, passengers.get(0), 2, StatutReservation.CONFIRMEE);
            Reservation second = reservation(t, passengers.get(1), 2, StatutReservation.CONFIRMEE);
            Reservation otherReservation = reservation(other, passengers.get(2), 1, StatutReservation.CONFIRMEE);
            entityManager.flush();
            return new Fixture(c.getId(), v.getId(), passengers.stream().map(Passager::getId).toList(),
                    t.getId(), other.getId(), List.of(first.getId(), second.getId(), old.getId()),
                    otherReservation.getId(), t.getPointsArret().get(0).getId(), t.getPointsArret().get(1).getId());
        });
    }
    @AfterEach void cleanOnlyOwnData() {
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            for (String entity : List.of("Reservation", "PointArret")) {
                entityManager.createQuery("delete from " + entity + " r where r.trajet.id in (select t.id from Trajet t where t.conducteur.id = :id)")
                        .setParameter("id", fixture.conducteurId()).executeUpdate();
            }
            entityManager.createQuery("delete from Trajet t where t.conducteur.id = :id")
                    .setParameter("id", fixture.conducteurId()).executeUpdate();
            entityManager.createQuery("delete from Vehicule v where v.conducteur.id = :id")
                    .setParameter("id", fixture.conducteurId()).executeUpdate();
            entityManager.remove(entityManager.find(Conducteur.class, fixture.conducteurId()));
            for (Long id : fixture.passagers()) entityManager.remove(entityManager.find(Passager.class, id));
        });
    }

    @Test void cancelsTwoConfirmedReservationsKeepsHistoryAndDoesNotTouchOtherTrip() {
        var before = reservationService.getByTrajetForConducteur(fixture.conducteurId(), fixture.trajetId(), fixture.conducteurId());
        trajetService.cancel(fixture.conducteurId(), fixture.trajetId(), fixture.conducteurId());
        var after = reservationService.getByTrajetForConducteur(fixture.conducteurId(), fixture.trajetId(), fixture.conducteurId());
        assertThat(after).hasSize(3);
        for (int i = 0; i < after.size(); i++) {
            var original = before.get(i); var cancelled = after.get(i);
            assertThat(cancelled.id()).isEqualTo(original.id());
            assertThat(cancelled.passagerId()).isEqualTo(original.passagerId());
            assertThat(cancelled.montee()).isEqualTo(original.montee());
            assertThat(cancelled.descente()).isEqualTo(original.descente());
            assertThat(cancelled.montantTotal()).isEqualTo(original.montantTotal());
            assertThat(cancelled.nombrePlaces()).isEqualTo(original.nombrePlaces());
            assertThat(cancelled.dateReservation()).isEqualTo(original.dateReservation());
            assertThat(cancelled.statut()).isEqualTo(StatutReservation.ANNULEE);
            assertThat(cancelled.trajet().status()).isEqualTo(StatutTrajet.ANNULE);
        }
        assertThat(reservationService.getRemainingSeats(fixture.trajetId())).isEqualTo(4);
        assertOtherTripUnchanged();
    }
    @Test void rollsBackTripAndReservationsTogether() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            trajetService.cancel(fixture.conducteurId(), fixture.trajetId(), fixture.conducteurId());
            entityManager.flush();
            throw new IllegalStateException("Rollback volontaire du trajet et reservations");
        })).isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status -> {
            assertThat(trajetRepository.findById(fixture.trajetId()).orElseThrow().getStatus()).isEqualTo(StatutTrajet.COMPLET);
            assertThat(reservationRepository.sumPlacesByTrajetIdAndStatut(fixture.trajetId(), StatutReservation.CONFIRMEE)).isEqualTo(4);
        });
        assertOtherTripUnchanged();
    }
    @Test void cancelsEmptyPublishedTrip() {
        Long emptyId = tx.execute(status -> trip(entityManager.find(Conducteur.class, fixture.conducteurId()),
                entityManager.find(Vehicule.class, fixture.vehiculeId())).getId());
        trajetService.cancel(fixture.conducteurId(), emptyId, fixture.conducteurId());
        assertThat(trajetRepository.findById(emptyId).orElseThrow().getStatus()).isEqualTo(StatutTrajet.ANNULE);
    }
    @ParameterizedTest @EnumSource(value = StatutTrajet.class, names = {"ANNULE", "TERMINE"})
    void refusesFinalTripWithoutTouchingReservations(StatutTrajet status) {
        tx.executeWithoutResult(txStatus -> trajetRepository.findById(fixture.trajetId()).orElseThrow().setStatus(status));
        assertThatThrownBy(() -> trajetService.cancel(fixture.conducteurId(), fixture.trajetId(), fixture.conducteurId()))
                .isInstanceOf(BusinessException.class);
        assertThat(trajetRepository.findById(fixture.trajetId()).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(reservationService.getRemainingSeats(fixture.trajetId())).isZero();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void serializesTripCancellationWithNewBooking(boolean rollbackFirst) throws Exception {
        reservationService.cancel(fixture.passagers().get(0), fixture.reservations().get(0), fixture.passagers().get(0));
        runContenders(rollbackFirst, true);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void passengerCancellationNeverReopensCancelledTrip(boolean rollbackFirst) throws Exception {
        runContenders(rollbackFirst, false);
    }
    private void runContenders(boolean rollbackFirst, boolean bookingSecond) throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        var firstChanged = new CountDownLatch(1); var releaseFirst = new CountDownLatch(1); var secondStarted = new CountDownLatch(1);
        var firstPid = new AtomicInteger(); var secondPid = new AtomicInteger();
        try {
            Future<?> first = workers.submit(() -> tx.executeWithoutResult(status -> {
                firstPid.set(backendPid());
                trajetService.cancel(fixture.conducteurId(), fixture.trajetId(), fixture.conducteurId());
                entityManager.flush(); firstChanged.countDown(); await(releaseFirst);
                if (rollbackFirst) status.setRollbackOnly();
            }));
            assertThat(firstChanged.await(15, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = workers.submit(() -> {
                try {
                    tx.executeWithoutResult(status -> {
                        secondPid.set(backendPid()); secondStarted.countDown();
                        if (bookingSecond) reservationService.create(fixture.passagers().get(2), fixture.passagers().get(2),
                                new ReservationCreateRequest(fixture.trajetId(), fixture.monteeId(), fixture.descenteId(), 1));
                        else reservationService.cancel(fixture.passagers().get(0), fixture.reservations().get(0), fixture.passagers().get(0));
                    });
                    return true;
                } catch (BusinessException e) { return false; }
            });
            assertThat(secondStarted.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(secondPid.get()).isNotEqualTo(firstPid.get());
            boolean blocked = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!blocked && System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(tx.execute(status -> entityManager.createNativeQuery(
                        "select :firstPid = any(pg_blocking_pids(:secondPid))", Boolean.class)
                        .setParameter("firstPid", firstPid.get()).setParameter("secondPid", secondPid.get()).getSingleResult()));
                if (!blocked) Thread.sleep(25);
            }
            assertThat(blocked).isTrue(); assertThat(second.isDone()).isFalse();
            releaseFirst.countDown(); first.get(15, TimeUnit.SECONDS);
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(rollbackFirst);
            tx.executeWithoutResult(status -> {
                var trip = trajetRepository.findById(fixture.trajetId()).orElseThrow();
                assertThat(trip.getStatus()).isEqualTo(rollbackFirst ? StatutTrajet.PUBLIE : StatutTrajet.ANNULE);
                assertThat(reservationRepository.sumPlacesByTrajetIdAndStatut(fixture.trajetId(), StatutReservation.CONFIRMEE))
                        .isEqualTo(rollbackFirst ? (bookingSecond ? 3L : 2L) : 0L);
            });
        } finally {
            releaseFirst.countDown(); workers.shutdown();
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) { workers.shutdownNow(); throw new AssertionError("Transactions non terminees"); }
        }
    }
    private void assertOtherTripUnchanged() {
        tx.executeWithoutResult(status -> {
            assertThat(trajetRepository.findById(fixture.otherTrajetId()).orElseThrow().getStatus()).isEqualTo(StatutTrajet.PUBLIE);
            assertThat(reservationRepository.findById(fixture.otherReservationId()).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        });
    }
    private int backendPid() { return ((Number) entityManager.createNativeQuery("select pg_backend_pid()").getSingleResult()).intValue(); }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Synchronisation expiree"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    private Passager passenger() {
        Passager p = new Passager(); p.setFirstName("Test"); p.setLastName("Annulation trajet"); p.setPhone("0600000000");
        p.setEmail(UUID.randomUUID() + "@example.com"); entityManager.persist(p); return p;
    }
    private Trajet trip(Conducteur c, Vehicule v) {
        Trajet t = new Trajet(); t.setConducteur(c); t.setVehicule(v); t.setOfferedSeats(4);
        t.setDepartureCity("Rabat"); t.setArrivalCity("Marrakech"); t.setDepartureTime(LocalDateTime.now().plusDays(3));
        for (int i = 0; i < 2; i++) {
            PointArret p = new PointArret(); p.setCity(i == 0 ? "Rabat" : "Marrakech"); p.setStopOrder(i);
            p.setPassageTime(t.getDepartureTime().plusHours(i * 4)); p.setPriceFromDeparture(new BigDecimal(i == 0 ? "0.00" : "120.00"));
            t.addPointArret(p);
        }
        entityManager.persist(t); return t;
    }
    private Reservation reservation(Trajet t, Passager p, int seats, StatutReservation status) {
        Reservation r = new Reservation(); r.setTrajet(t); r.setPassager(p);
        r.setMontee(t.getPointsArret().get(0)); r.setDescente(t.getPointsArret().get(1));
        r.setNombrePlaces(seats); r.setMontantTotal(new BigDecimal("120.00").multiply(BigDecimal.valueOf(seats)));
        r.setStatut(status); r.setDateReservation(LocalDateTime.now()); entityManager.persist(r); return r;
    }
}
