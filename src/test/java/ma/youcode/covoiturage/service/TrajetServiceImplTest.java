package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.trajet.*;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.StatutTrajet;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.mapper.*;
import ma.youcode.covoiturage.repository.*;
import ma.youcode.covoiturage.service.impl.TrajetServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrajetServiceImplTest {
    @Mock private ConducteurRepository conducteurRepository;
    @Mock private VehiculeRepository vehiculeRepository;
    @Mock private TrajetRepository trajetRepository;
    @Mock private TrajetMapper trajetMapper;
    @Spy private PointArretMapper pointMapper = Mappers.getMapper(PointArretMapper.class);
    @InjectMocks private TrajetServiceImpl service;
    private Conducteur owner;
    private Vehicule vehicle;
    private LocalDateTime departure;

    @BeforeEach void prepare() {
        owner = new Conducteur(); owner.setId(1L);
        vehicle = new Vehicule(); vehicle.setId(2L);
        vehicle.setCapacity(4); vehicle.setConducteur(owner);
        departure = LocalDateTime.now().plusDays(2);
    }

    private List<PointArretCreateRequest> validPoints() {
        return List.of(new PointArretCreateRequest(" Rabat ", departure, new BigDecimal("0.00")),
                new PointArretCreateRequest("Casablanca", departure.plusHours(1), new BigDecimal("40")),
                new PointArretCreateRequest("Marrakech", departure.plusHours(3), new BigDecimal("120")));
    }

    private void existingOwnerAndVehicle() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(vehiculeRepository.findById(2L)).thenReturn(Optional.of(vehicle));
    }

    @Test void publishesWithOrderedPointsAndDerivedDeparture() {
        existingOwnerAndVehicle();
        TrajetCreateRequest request = new TrajetCreateRequest(2L, 3, validPoints());
        Trajet entity = new Trajet(); entity.setOfferedSeats(3);
        when(trajetMapper.toEntity(request)).thenReturn(entity);
        when(trajetRepository.save(entity)).thenReturn(entity);

        service.create(1L, 1L, request);

        assertThat(entity.getConducteur()).isSameAs(owner);
        assertThat(entity.getVehicule()).isSameAs(vehicle);
        assertThat(entity.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
        assertThat(entity.getDepartureCity()).isEqualTo("Rabat");
        assertThat(entity.getArrivalCity()).isEqualTo("Marrakech");
        assertThat(entity.getDepartureTime()).isEqualTo(departure);
        assertThat(entity.getPointsArret()).extracting(PointArret::getStopOrder).containsExactly(0, 1, 2);
        assertThat(entity.getPointsArret()).allSatisfy(point -> {
            assertThat(point.getTrajet()).isSameAs(entity);
            assertThat(point.getPriceFromDeparture().scale()).isEqualTo(2);
        });
        verify(trajetRepository).save(entity);
    }

    @Test void rejectsMissingConducteur() {
        assertThatThrownBy(() -> service.create(1L, 1L, new TrajetCreateRequest(2L, 3, validPoints())))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(trajetRepository);
    }

    @Test void rejectsAnotherActor() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.create(1L, 9L, new TrajetCreateRequest(2L, 3, validPoints())))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(vehiculeRepository, trajetRepository);
    }

    @Test void rejectsMissingVehicle() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.create(1L, 1L, new TrajetCreateRequest(2L, 3, validPoints())))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(trajetRepository);
    }

    @Test void rejectsAnotherConducteursVehicle() {
        existingOwnerAndVehicle();
        Conducteur other = new Conducteur(); other.setId(9L); vehicle.setConducteur(other);
        assertThatThrownBy(() -> service.create(1L, 1L, new TrajetCreateRequest(2L, 3, validPoints())))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(trajetRepository);
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, 5})
    void rejectsInvalidSeats(int seats) {
        existingOwnerAndVehicle();
        assertThatThrownBy(() -> service.create(1L, 1L, new TrajetCreateRequest(2L, seats, validPoints())))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(trajetRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"onePoint", "past", "firstPrice", "duplicateCity", "sameTime", "earlierTime", "samePrice", "lowerPrice", "negativePrice", "fraction"})
    void rejectsInvalidParcours(String scenario) {
        existingOwnerAndVehicle();
        List<PointArretCreateRequest> points = new java.util.ArrayList<>(validPoints());
        switch (scenario) {
            case "onePoint" -> points = List.of(points.get(0));
            case "past" -> points.set(0, new PointArretCreateRequest("Rabat", departure.minusDays(3), BigDecimal.ZERO));
            case "firstPrice" -> points.set(0, new PointArretCreateRequest("Rabat", departure, BigDecimal.ONE));
            case "duplicateCity" -> points.set(1, new PointArretCreateRequest(" RABAT ", departure.plusHours(1), new BigDecimal("40")));
            case "sameTime" -> points.set(1, new PointArretCreateRequest("Casa", departure, new BigDecimal("40")));
            case "earlierTime" -> points.set(1, new PointArretCreateRequest("Casa", departure.minusHours(1), new BigDecimal("40")));
            case "samePrice" -> points.set(2, new PointArretCreateRequest("Marrakech", departure.plusHours(3), new BigDecimal("40.00")));
            case "lowerPrice" -> points.set(2, new PointArretCreateRequest("Marrakech", departure.plusHours(3), new BigDecimal("30")));
            case "negativePrice" -> points.set(1, new PointArretCreateRequest("Casa", departure.plusHours(1), new BigDecimal("-1")));
            case "fraction" -> points.set(1, new PointArretCreateRequest("Casa", departure.plusHours(1), new BigDecimal("40.001")));
        }
        TrajetCreateRequest request = new TrajetCreateRequest(2L, 3, points);
        assertThatThrownBy(() -> service.create(1L, 1L, request)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(trajetRepository);
    }
}
