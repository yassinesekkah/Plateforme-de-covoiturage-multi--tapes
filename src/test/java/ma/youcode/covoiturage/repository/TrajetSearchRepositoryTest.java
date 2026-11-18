package ma.youcode.covoiturage.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchResult;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class TrajetSearchRepositoryTest {
    @Autowired private TrajetRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    private static final LocalDate DAY = LocalDate.of(2030, 1, 1);
    private Conducteur conducteur;
    private Vehicule vehicule;
    private String casa;
    private String settat;

    @BeforeEach void prepare() {
        String suffix = UUID.randomUUID().toString();
        casa = "Casablanca-" + suffix; settat = "Settat-" + suffix;
        conducteur = new Conducteur();
        conducteur.setFirstName("Test"); conducteur.setLastName("Recherche");
        conducteur.setEmail(suffix + "@example.com"); conducteur.setLicenseNumber(suffix);
        conducteur.setPhone("0600000000"); entityManager.persist(conducteur);
        vehicule = new Vehicule();
        vehicule.setBrand("Dacia"); vehicule.setModel("Logan"); vehicule.setColor("Blanc");
        vehicule.setCapacity(4); vehicule.setRegistrationNumber(suffix);
        vehicule.setConducteur(conducteur); entityManager.persist(vehicule);
    }

    @Test void returnsIntermediateSegmentWithPriceAndSeatsInOneQuery() {
        Trajet t = trip(DAY.atTime(17, 0));
        entityManager.flush(); entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var result = repository.search(casa.toUpperCase(java.util.Locale.ROOT), settat.toLowerCase(java.util.Locale.ROOT),
                DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(), 2, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        var row = result.getContent().get(0);
        assertThat(row.trajetId()).isEqualTo(t.getId());
        assertThat(row.monteeId()).isEqualTo(t.getPointsArret().get(1).getId());
        assertThat(row.descenteId()).isEqualTo(t.getPointsArret().get(2).getId());
        assertThat(row.departureCity()).isEqualTo(casa);
        assertThat(row.arrivalCity()).isEqualTo(settat);
        assertThat(row.departureTime()).isEqualTo(DAY.atTime(17, 0));
        assertThat(row.monteeTime()).isEqualTo(DAY.atTime(18, 0));
        assertThat(row.descenteTime()).isEqualTo(DAY.atTime(19, 0));
        assertThat(row.segmentPrice()).isEqualByComparingTo("30.00");
        assertThat(row.remainingSeats()).isEqualTo(4L);
        assertThat(result.hasNext()).isFalse();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test void rejectsReverseSameCityAndUnknownCities() {
        trip(DAY.atTime(17, 0));
        assertThat(repository.search(settat, casa, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                1, null, PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(repository.search(casa, casa, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                1, null, PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(repository.search(casa + "-absent", settat, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                1, null, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test void neverCombinesPointsFromDifferentTrips() {
        Trajet first = trip(DAY.atTime(10, 0));
        Trajet second = trip(DAY.atTime(12, 0));
        first.getPointsArret().get(2).setCity(settat + "-other");
        second.getPointsArret().get(1).setCity(casa + "-other");
        assertThat(search(1, null, 0, 10).getContent()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = StatutTrajet.class, names = {"COMPLET", "ANNULE", "TERMINE"})
    void excludesUnpublishedTrips(StatutTrajet statut) {
        trip(DAY.atTime(17, 0)).setStatus(statut);
        assertThat(search(1, null, 0, 10).getContent()).isEmpty();
    }

    @Test void countsOnlyConfirmedSeatsAcrossEntireTripAndIgnoresOtherTrips() {
        Trajet target = trip(DAY.atTime(17, 0));
        reservation(target, 1, StatutReservation.CONFIRMEE, 0, 1);
        reservation(target, 1, StatutReservation.CONFIRMEE, 2, 3);
        reservation(target, 2, StatutReservation.ANNULEE, 1, 2);
        Trajet other = trip(DAY.atTime(19, 0));
        reservation(other, 4, StatutReservation.CONFIRMEE, 0, 3);
        var rows = search(2, null, 0, 10).getContent();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).trajetId()).isEqualTo(target.getId());
        assertThat(rows.get(0).remainingSeats()).isEqualTo(2L);
        assertThat(search(3, null, 0, 10).getContent()).isEmpty();
    }

    @Test void appliesMaximumPricePerSeatToRequestedSegmentInclusively() {
        trip(DAY.atTime(17, 0));
        assertThat(search(2, new BigDecimal("30.00"), 0, 10).getContent()).hasSize(1);
        assertThat(search(2, new BigDecimal("29.99"), 0, 10).getContent()).isEmpty();
        assertThat(search(1, BigDecimal.ZERO, 0, 10).getContent()).isEmpty();
    }

    @Test void filtersWholeDepartureDayWithExclusiveNextDayBoundary() {
        trip(DAY.minusDays(1).atTime(23, 59, 59));
        Trajet first = trip(DAY.atStartOfDay());
        Trajet last = trip(DAY.atTime(23, 59, 59));
        trip(DAY.plusDays(1).atStartOfDay());
        assertThat(search(1, null, 0, 10).getContent()).extracting(TrajetSearchResult::trajetId)
                .containsExactly(first.getId(), last.getId());
    }

    @Test void dateUsesTripDepartureEvenWhenBoardingIsNextDay() {
        Trajet t = trip(DAY.atTime(23, 30));
        var rows = search(1, null, 0, 10).getContent();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).monteeTime().toLocalDate()).isEqualTo(DAY.plusDays(1));
        assertThat(rows.get(0).trajetId()).isEqualTo(t.getId());
    }

    @Test void paginatesStablyWithHasNextAndOneQueryPerSlice() {
        Trajet late = trip(DAY.atTime(19, 0));
        Trajet early = trip(DAY.atTime(10, 0));
        Trajet sameTime = trip(DAY.atTime(10, 0));
        entityManager.flush(); entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var first = search(1, null, 0, 2);
        assertThat(first.getContent()).extracting(TrajetSearchResult::trajetId)
                .containsExactly(early.getId(), sameTime.getId());
        assertThat(first.hasNext()).isTrue();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        statistics.clear();
        var second = search(1, null, 1, 2);
        assertThat(second.getContent()).extracting(TrajetSearchResult::trajetId).containsExactly(late.getId());
        assertThat(second.hasNext()).isFalse();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(search(1, null, 2, 2).getContent()).isEmpty();
    }

    private Slice<TrajetSearchResult> search(int seats, BigDecimal maxPrice, int page, int size) {
        return repository.search(casa, settat, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(),
                seats, maxPrice, PageRequest.of(page, size));
    }
    private Trajet trip(LocalDateTime departure) {
        Trajet t = new Trajet(); t.setConducteur(conducteur); t.setVehicule(vehicule);
        t.setDepartureCity("Rabat"); t.setArrivalCity("Marrakech");
        t.setDepartureTime(departure); t.setOfferedSeats(4);
        String[] cities = {"Rabat", casa, settat, "Marrakech"};
        String[] prices = {"0.00", "40.00", "70.00", "120.00"};
        for (int i = 0; i < 4; i++) {
            PointArret p = new PointArret(); p.setCity(cities[i]); p.setStopOrder(i);
            p.setPassageTime(departure.plusHours(i)); p.setPriceFromDeparture(new BigDecimal(prices[i]));
            t.addPointArret(p);
        }
        entityManager.persist(t); return t;
    }
    private void reservation(Trajet t, int seats, StatutReservation statut, int from, int to) {
        Passager p = new Passager(); p.setFirstName("Test"); p.setLastName("Recherche");
        p.setPhone("0600000000"); p.setEmail(UUID.randomUUID() + "@example.com"); entityManager.persist(p);
        Reservation r = new Reservation(); r.setPassager(p); r.setTrajet(t);
        r.setMontee(t.getPointsArret().get(from)); r.setDescente(t.getPointsArret().get(to));
        r.setNombrePlaces(seats); r.setStatut(statut); r.setDateReservation(LocalDateTime.now());
        r.setMontantTotal(new BigDecimal("10.00")); entityManager.persist(r);
    }
}
