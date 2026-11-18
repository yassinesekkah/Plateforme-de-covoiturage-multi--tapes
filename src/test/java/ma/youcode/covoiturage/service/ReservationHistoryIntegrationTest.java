package ma.youcode.covoiturage.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ma.youcode.covoiturage.dto.reservation.ReservationDetailResponse;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ma.youcode.covoiturage.exception.ForbiddenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ReservationHistoryIntegrationTest {
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ReservationService service;
    private Passager passenger;
    private Passager other;
    private Passager empty;
    private Conducteur conducteur;
    private Vehicule vehicule;

    @BeforeEach void prepare() {
        String suffix = UUID.randomUUID().toString();
        conducteur = new Conducteur(); conducteur.setFirstName("Test"); conducteur.setLastName("Historique");
        conducteur.setPhone("0600000000"); conducteur.setEmail(suffix + "@example.com");
        conducteur.setLicenseNumber(suffix); entityManager.persist(conducteur);
        vehicule = new Vehicule(); vehicule.setBrand("Dacia"); vehicule.setModel("Logan");
        vehicule.setColor("Blanc"); vehicule.setCapacity(4); vehicule.setRegistrationNumber(suffix);
        vehicule.setConducteur(conducteur); entityManager.persist(vehicule);
        passenger = passenger(); other = passenger(); empty = passenger();
    }

    @Test void readsOnlyOwnFullHistoryInStableOrderWithTwoQueries() {
        var old = reservation(passenger, StatutReservation.CONFIRMEE, StatutTrajet.TERMINE,
                LocalDateTime.of(2029, 12, 1, 12, 0), 1);
        var cancelled = reservation(passenger, StatutReservation.ANNULEE, StatutTrajet.ANNULE,
                LocalDateTime.of(2030, 1, 1, 12, 0), 2);
        var latest = reservation(passenger, StatutReservation.CONFIRMEE, StatutTrajet.PUBLIE,
                LocalDateTime.of(2030, 1, 1, 12, 0), 1);
        reservation(other, StatutReservation.CONFIRMEE, StatutTrajet.PUBLIE,
                LocalDateTime.of(2030, 1, 2, 12, 0), 1);
        entityManager.flush(); entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var history = service.getByPassager(passenger.getId(), passenger.getId());

        assertThat(history).extracting(ReservationDetailResponse::id)
                .containsExactly(latest.getId(), cancelled.getId(), old.getId());
        assertThat(history).extracting(ReservationDetailResponse::statut)
                .containsExactly(StatutReservation.CONFIRMEE, StatutReservation.ANNULEE, StatutReservation.CONFIRMEE);
        assertThat(history).extracting(r -> r.trajet().status())
                .containsExactly(StatutTrajet.PUBLIE, StatutTrajet.ANNULE, StatutTrajet.TERMINE);
        assertThat(history).allSatisfy(r -> {
            assertThat(r.passagerId()).isEqualTo(passenger.getId());
            assertThat(r.trajet().departureCity()).isEqualTo("Rabat");
            assertThat(r.trajet().arrivalCity()).isEqualTo("Marrakech");
            assertThat(r.montee().city()).isEqualTo("Rabat");
            assertThat(r.descente().city()).isEqualTo("Marrakech");
            assertThat(r.montee().passageTime()).isEqualTo(r.trajet().departureTime());
            assertThat(r.descente().passageTime()).isEqualTo(r.trajet().departureTime().plusHours(4));
            assertThat(r.montantTotal()).isEqualByComparingTo(new BigDecimal("120.00").multiply(BigDecimal.valueOf(r.nombrePlaces())));
        });
        assertThat(history.get(1).nombrePlaces()).isEqualTo(2);
        assertThat(history.get(1).dateReservation()).isEqualTo(cancelled.getDateReservation());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
    }

    @Test void passengerWithoutReservationsGetsEmptyHistoryEvenIfOthersHaveSome() {
        reservation(other, StatutReservation.CONFIRMEE, StatutTrajet.PUBLIE,
                LocalDateTime.of(2030, 1, 1, 12, 0), 1);
        entityManager.flush(); entityManager.clear();
        assertThat(service.getByPassager(empty.getId(), empty.getId())).isEmpty();
    }

    @ParameterizedTest @EnumSource(StatutTrajet.class)
    void conducteurReadsOnlySelectedTripInStableOrderWithThreeQueries(StatutTrajet statut) {
        Trajet target = trip(statut);
        var old = reservation(passenger, StatutReservation.CONFIRMEE, target,
                LocalDateTime.of(2029, 12, 1, 12, 0), 1);
        var cancelled = reservation(other, StatutReservation.ANNULEE, target,
                LocalDateTime.of(2030, 1, 1, 12, 0), 2);
        var latest = reservation(empty, StatutReservation.CONFIRMEE, target,
                LocalDateTime.of(2030, 1, 1, 12, 0), 1);
        reservation(other, StatutReservation.CONFIRMEE, StatutTrajet.PUBLIE,
                LocalDateTime.of(2030, 1, 2, 12, 0), 1);
        entityManager.flush(); entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var rows = service.getByTrajetForConducteur(conducteur.getId(), target.getId(), conducteur.getId());

        assertThat(rows).extracting(ReservationDetailResponse::id)
                .containsExactly(latest.getId(), cancelled.getId(), old.getId());
        assertThat(rows).extracting(ReservationDetailResponse::passagerId)
                .containsExactly(empty.getId(), other.getId(), passenger.getId());
        assertThat(rows).extracting(ReservationDetailResponse::statut)
                .containsExactly(StatutReservation.CONFIRMEE, StatutReservation.ANNULEE, StatutReservation.CONFIRMEE);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.trajet().id()).isEqualTo(target.getId());
            assertThat(r.trajet().status()).isEqualTo(statut);
            assertThat(r.montee().city()).isEqualTo("Rabat");
            assertThat(r.descente().city()).isEqualTo("Marrakech");
            assertThat(r.montantTotal()).isEqualByComparingTo(new BigDecimal("120.00").multiply(BigDecimal.valueOf(r.nombrePlaces())));
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(statistics.getEntityInsertCount()).isZero();
        assertThat(statistics.getEntityUpdateCount()).isZero();
        assertThat(statistics.getEntityDeleteCount()).isZero();
    }

    @Test void ownEmptyTripReturnsEmptyListAndForeignOwnerIsRefused() {
        Trajet target = trip(StatutTrajet.PUBLIE);
        reservation(other, StatutReservation.CONFIRMEE, StatutTrajet.PUBLIE,
                LocalDateTime.of(2030, 1, 1, 12, 0), 1);
        Conducteur stranger = new Conducteur(); stranger.setFirstName("Autre"); stranger.setLastName("Test");
        stranger.setPhone("0600000000"); stranger.setEmail(UUID.randomUUID() + "@example.com");
        stranger.setLicenseNumber(UUID.randomUUID().toString()); entityManager.persist(stranger);
        entityManager.flush(); entityManager.clear();

        assertThat(service.getByTrajetForConducteur(conducteur.getId(), target.getId(), conducteur.getId())).isEmpty();
        assertThatThrownBy(() -> service.getByTrajetForConducteur(stranger.getId(), target.getId(), stranger.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    private Passager passenger() {
        Passager p = new Passager(); p.setFirstName("Test"); p.setLastName("Historique");
        p.setPhone("0600000000"); p.setEmail(UUID.randomUUID() + "@example.com");
        entityManager.persist(p); return p;
    }
    private Reservation reservation(Passager p, StatutReservation statut, StatutTrajet tripStatus,
                                    LocalDateTime date, int seats) {
        return reservation(p, statut, trip(tripStatus), date, seats);
    }
    private Trajet trip(StatutTrajet tripStatus) {
        LocalDateTime departure = LocalDateTime.of(2030, 2, 1, 17, 0);
        Trajet t = new Trajet(); t.setConducteur(conducteur); t.setVehicule(vehicule);
        t.setDepartureCity("Rabat"); t.setArrivalCity("Marrakech");
        t.setDepartureTime(departure); t.setOfferedSeats(4); t.setStatus(tripStatus);
        PointArret montee = point("Rabat", 0, departure, "0.00");
        PointArret descente = point("Marrakech", 1, departure.plusHours(4), "120.00");
        t.addPointArret(montee); t.addPointArret(descente); entityManager.persist(t);
        return t;
    }
    private Reservation reservation(Passager p, StatutReservation statut, Trajet t,
                                    LocalDateTime date, int seats) {
        Reservation r = new Reservation(); r.setPassager(p); r.setTrajet(t);
        r.setMontee(t.getPointsArret().get(0)); r.setDescente(t.getPointsArret().get(1)); r.setNombrePlaces(seats);
        r.setMontantTotal(new BigDecimal("120.00").multiply(BigDecimal.valueOf(seats)));
        r.setDateReservation(date); r.setStatut(statut); entityManager.persist(r); return r;
    }
    private PointArret point(String city, int order, LocalDateTime time, String price) {
        PointArret p = new PointArret(); p.setCity(city); p.setStopOrder(order);
        p.setPassageTime(time); p.setPriceFromDeparture(new BigDecimal(price)); return p;
    }
}
