package ma.youcode.covoiturage.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import ma.youcode.covoiturage.dto.trajet.*;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.StatutTrajet;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TrajetPublicationIntegrationTest {
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TrajetService trajetService;
    @Autowired private VehiculeService vehiculeService;
    @Autowired private ConducteurRepository conducteurRepository;
    @Autowired private VehiculeRepository vehiculeRepository;
    @Autowired private TrajetRepository trajetRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;
    private Long conducteurId;
    private Long vehiculeId;

    @BeforeEach void createOwnTestData() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            Conducteur c = new Conducteur();
            c.setLastName("Test"); c.setFirstName("Publication");
            c.setEmail("publication-" + suffix + "@example.com");
            c.setPhone("0600000000"); c.setLicenseNumber("TEST-" + suffix);
            conducteurId = conducteurRepository.save(c).getId();
            Vehicule v = new Vehicule();
            v.setBrand("Dacia"); v.setModel("Logan"); v.setColor("Blanc");
            v.setRegistrationNumber("TEST-" + suffix); v.setCapacity(4); v.setConducteur(c);
            vehiculeId = vehiculeRepository.save(v).getId();
        });
    }

    @AfterEach void removeOnlyOwnTestData() {
        if (conducteurId == null) return;
        tx.executeWithoutResult(status -> {
            List<Trajet> trips = entityManager.createQuery(
                    "select t from Trajet t where t.conducteur.id = :id", Trajet.class)
                    .setParameter("id", conducteurId).getResultList();
            trajetRepository.deleteAll(trips);
            trajetRepository.flush();
            if (vehiculeId != null) {
                vehiculeRepository.deleteById(vehiculeId);
                vehiculeRepository.flush();
            }
            conducteurRepository.deleteById(conducteurId);
        });
    }

    private TrajetCreateRequest request() {
        LocalDateTime time = LocalDateTime.now().plusDays(2);
        return new TrajetCreateRequest(vehiculeId, 3, List.of(
                new PointArretCreateRequest(" Rabat ", time, BigDecimal.ZERO),
                new PointArretCreateRequest("Casablanca", time.plusHours(1), new BigDecimal("40")),
                new PointArretCreateRequest("Marrakech", time.plusHours(3), new BigDecimal("120"))));
    }

    @Test void savesTripAndAllPointsAndReloadsThemInOrder() {
        TrajetResponse response = trajetService.create(conducteurId, conducteurId, request());
        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(StatutTrajet.PUBLIE);
        assertThat(response.pointsArret()).extracting(PointArretResponse::stopOrder).containsExactly(0, 1, 2);
        assertThat(response.pointsArret()).allSatisfy(point -> assertThat(point.id()).isNotNull());
        tx.executeWithoutResult(status -> {
            Trajet saved = trajetRepository.findById(response.id()).orElseThrow();
            assertThat(saved.getDepartureCity()).isEqualTo("Rabat");
            assertThat(saved.getArrivalCity()).isEqualTo("Marrakech");
            assertThat(saved.getPointsArret()).extracting(PointArret::getStopOrder).containsExactly(0, 1, 2);
            assertThat(saved.getPointsArret()).allSatisfy(point -> {
                assertThat(point.getTrajet().getId()).isEqualTo(saved.getId());
                assertThat(point.getPriceFromDeparture().scale()).isEqualTo(2);
            });
        });
    }

    @Test void rollsBackTripAndPointsTogether() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            trajetService.create(conducteurId, conducteurId, request());
            throw new IllegalStateException("Rollback volontaire");
        })).isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status -> {
            assertThat(trajetRepository.existsByVehiculeId(vehiculeId)).isFalse();
            Long count = entityManager.createQuery(
                    "select count(p) from PointArret p where p.trajet.conducteur.id = :id", Long.class)
                    .setParameter("id", conducteurId).getSingleResult();
            assertThat(count).isZero();
        });
    }

    @Test void readsFullDetailAndRemainingSeatsInTwoQueries() {
        TrajetResponse created = trajetService.create(conducteurId, conducteurId, request());
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        TrajetDetailResponse detail = trajetService.getById(created.id());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(detail.id()).isEqualTo(created.id());
        assertThat(detail.conducteur().id()).isEqualTo(conducteurId);
        assertThat(detail.conducteur().firstName()).isEqualTo("Publication");
        assertThat(detail.conducteur().phone()).isEqualTo("0600000000");
        assertThat(detail.vehicule().id()).isEqualTo(vehiculeId);
        assertThat(detail.vehicule().conducteurId()).isEqualTo(conducteurId);
        assertThat(detail.vehicule().model()).isEqualTo("Logan");
        assertThat(detail.departureCity()).isEqualTo("Rabat");
        assertThat(detail.arrivalCity()).isEqualTo("Marrakech");
        assertThat(detail.departureTime()).isCloseTo(created.departureTime(),
                within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(detail.offeredSeats()).isEqualTo(3);
        assertThat(detail.remainingSeats()).isEqualTo(3);
        assertThat(detail.status()).isEqualTo(StatutTrajet.PUBLIE);
        assertThat(detail.pointsArret()).extracting(PointArretResponse::city)
                .containsExactly("Rabat", "Casablanca", "Marrakech");
        assertThat(detail.pointsArret()).extracting(PointArretResponse::stopOrder)
                .containsExactly(0, 1, 2);
        assertThat(detail.pointsArret()).extracting(PointArretResponse::priceFromDeparture)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("40.00"), new BigDecimal("120.00"));
    }

    @Test void refusesVehicleDeletionAndKeepsTripAndVehicle() {
        TrajetResponse trip = trajetService.create(conducteurId, conducteurId, request());
        assertThatThrownBy(() -> vehiculeService.delete(vehiculeId, conducteurId))
                .isInstanceOf(BusinessException.class);
        assertThat(vehiculeRepository.existsById(vehiculeId)).isTrue();
        assertThat(trajetRepository.existsById(trip.id())).isTrue();
    }

    @Test void databaseAlsoBlocksDeletionWhenServiceCheckIsBypassed() {
        TrajetResponse trip = trajetService.create(conducteurId, conducteurId, request());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            vehiculeRepository.deleteById(vehiculeId);
            vehiculeRepository.flush();
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(vehiculeRepository.existsById(vehiculeId)).isTrue();
        assertThat(trajetRepository.existsById(trip.id())).isTrue();
    }
}
