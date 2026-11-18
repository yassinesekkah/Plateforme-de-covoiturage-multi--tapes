package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.reservation.*;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.mapper.ReservationMapper;
import ma.youcode.covoiturage.repository.*;
import ma.youcode.covoiturage.service.impl.ReservationServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {
    @Mock private PassagerRepository passagerRepository;
    @Mock private TrajetRepository trajetRepository;
    @Mock private PointArretRepository pointArretRepository;
    @Mock private ReservationRepository reservationRepository;
    @Spy private ReservationMapper mapper = Mappers.getMapper(ReservationMapper.class);
    @InjectMocks private ReservationServiceImpl service;
    private Passager passager;
    private Trajet trajet;
    private PointArret montee;
    private PointArret descente;

    @BeforeEach void prepare() {
        passager = new Passager(); passager.setId(1L);
        trajet = new Trajet(); trajet.setId(10L); trajet.setOfferedSeats(4);
        montee = point(20L, 1, "40.00");
        descente = point(21L, 3, "120.00");
    }
    private PointArret point(Long id, int order, String price) {
        PointArret p = new PointArret(); p.setId(id); p.setTrajet(trajet);
        p.setStopOrder(order); p.setPriceFromDeparture(new BigDecimal(price)); return p;
    }
    private ReservationCreateRequest request(Integer seats) {
        return new ReservationCreateRequest(10L, 20L, 21L, seats);
    }
    private void existingPassengerAndTrip() {
        when(passagerRepository.findById(1L)).thenReturn(Optional.of(passager));
        when(trajetRepository.findByIdForReservation(10L)).thenReturn(Optional.of(trajet));
    }
    private void existingPoints() {
        when(pointArretRepository.findById(20L)).thenReturn(Optional.of(montee));
        when(pointArretRepository.findById(21L)).thenReturn(Optional.of(descente));
    }
    private void saveReturnsEntity() {
        when(reservationRepository.save(any())).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0); r.setId(100L); return r;
        });
    }

    @Test void calculates160DhForTwoSeatsAndKeepsTripPublished() {
        existingPassengerAndTrip(); existingPoints(); saveReturnsEntity();
        when(reservationRepository.sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(1L);
        ReservationResponse response = service.create(1L, 1L, request(2));
        assertThat(response.montantTotal()).isEqualByComparingTo("160.00");
        assertThat(response.montantTotal().scale()).isEqualTo(2);
        assertThat(response.nombrePlaces()).isEqualTo(2);
        assertThat(response.passagerId()).isEqualTo(1L);
        assertThat(response.monteeId()).isEqualTo(20L);
        assertThat(response.descenteId()).isEqualTo(21L);
        assertThat(response.statut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(response.dateReservation()).isNotNull();
        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
    }
    @Test void calculates50DhAndMarksTripFullForLastSeat() {
        existingPassengerAndTrip(); existingPoints(); saveReturnsEntity();
        montee.setPriceFromDeparture(new BigDecimal("70.00"));
        when(reservationRepository.sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(3L);
        assertThat(service.create(1L, 1L, request(1)).montantTotal()).isEqualByComparingTo("50.00");
        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.COMPLET);
    }
    @Test void rejectsMissingPassenger() {
        assertThatThrownBy(() -> service.create(1L, 1L, request(1))).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(trajetRepository, reservationRepository);
    }
    @Test void rejectsAnotherActor() {
        when(passagerRepository.findById(1L)).thenReturn(Optional.of(passager));
        assertThatThrownBy(() -> service.create(1L, 2L, request(1))).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(trajetRepository, reservationRepository);
    }
    @Test void rejectsMissingTrip() {
        when(passagerRepository.findById(1L)).thenReturn(Optional.of(passager));
        assertThatThrownBy(() -> service.create(1L, 1L, request(1))).isInstanceOf(ResourceNotFoundException.class);
    }
    @ParameterizedTest
    @EnumSource(value = StatutTrajet.class, names = {"COMPLET", "TERMINE", "ANNULE"})
    void rejectsUnavailableTrip(StatutTrajet status) {
        existingPassengerAndTrip(); trajet.setStatus(status);
        assertThatThrownBy(() -> service.create(1L, 1L, request(1))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(pointArretRepository, reservationRepository);
    }
    @ParameterizedTest @NullSource @ValueSource(ints = {0, -1})
    void rejectsInvalidSeatCount(Integer seats) {
        existingPassengerAndTrip();
        assertThatThrownBy(() -> service.create(1L, 1L, request(seats))).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(reservationRepository);
    }
    @Test void rejectsMissingBoardingPoint() {
        existingPassengerAndTrip();
        assertThatThrownBy(() -> service.create(1L, 1L, request(1))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void rejectsMissingArrivalPoint() {
        existingPassengerAndTrip();
        when(pointArretRepository.findById(20L)).thenReturn(Optional.of(montee));
        assertThatThrownBy(() -> service.create(1L, 1L, request(1))).isInstanceOf(ResourceNotFoundException.class);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void rejectsEitherPointFromAnotherTrip(boolean boarding) {
        existingPassengerAndTrip(); existingPoints();
        Trajet other = new Trajet(); other.setId(99L);
        (boarding ? montee : descente).setTrajet(other);
        assertThatThrownBy(() -> service.create(1L, 1L, request(1))).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(reservationRepository);
    }
    @ParameterizedTest @ValueSource(ints = {1, 0})
    void rejectsEqualOrReversedStopOrder(int arrivalOrder) {
        existingPassengerAndTrip(); existingPoints(); descente.setStopOrder(arrivalOrder);
        assertThatThrownBy(() -> service.create(1L, 1L, request(1))).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(reservationRepository);
    }
    @Test void rejectsExistingConfirmedReservation() {
        existingPassengerAndTrip(); existingPoints();
        when(reservationRepository.existsByPassagerIdAndTrajetIdAndStatut(1L, 10L, StatutReservation.CONFIRMEE)).thenReturn(true);
        assertThatThrownBy(() -> service.create(1L, 1L, request(1))).isInstanceOf(BusinessException.class);
        verify(reservationRepository, never()).save(any());
    }
    @ParameterizedTest @ValueSource(longs = {3, 4})
    void rejectsInsufficientSeatsEvenForSmallSegment(long alreadyReserved) {
        existingPassengerAndTrip(); existingPoints();
        when(reservationRepository.sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(alreadyReserved);
        assertThatThrownBy(() -> service.create(1L, 1L, request(2))).isInstanceOf(BusinessException.class);
        verify(reservationRepository, never()).save(any());
        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
    }
    @Test void calculatesRemainingSeatsUsingConfirmedReservationsOnly() {
        when(trajetRepository.findById(10L)).thenReturn(Optional.of(trajet));
        when(reservationRepository.sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(2L);
        assertThat(service.getRemainingSeats(10L)).isEqualTo(2);
    }
    @Test void rejectsRemainingSeatsForMissingTrip() {
        assertThatThrownBy(() -> service.getRemainingSeats(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
