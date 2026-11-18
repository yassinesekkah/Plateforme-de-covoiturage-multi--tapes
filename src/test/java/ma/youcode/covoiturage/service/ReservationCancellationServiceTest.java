package ma.youcode.covoiturage.service;

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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationServiceTest {
    @Mock private PassagerRepository passagerRepository;
    @Mock private TrajetRepository trajetRepository;
    @Mock private PointArretRepository pointArretRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationMapper mapper;
    @Mock private ConducteurRepository conducteurRepository;
    private ReservationServiceImpl service;
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 12, 0);
    private Passager passenger;
    private Trajet trip;
    private Reservation reservation;

    @BeforeEach void prepare() {
        Clock fixed = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ReservationServiceImpl(passagerRepository, trajetRepository, pointArretRepository,
                reservationRepository, mapper, conducteurRepository, fixed);
        passenger = new Passager(); passenger.setId(1L);
        trip = new Trajet(); trip.setId(10L); trip.setOfferedSeats(4);
        trip.setStatus(StatutTrajet.COMPLET); trip.setDepartureTime(NOW.plusDays(3));
        reservation = new Reservation(); reservation.setId(20L); reservation.setPassager(passenger);
        reservation.setTrajet(trip); reservation.setStatut(StatutReservation.CONFIRMEE);
        reservation.setNombrePlaces(2); reservation.setMontantTotal(new BigDecimal("160.00"));
        reservation.setDateReservation(NOW.minusDays(1));
    }
    private void passengerExists() {
        when(passagerRepository.findById(1L)).thenReturn(Optional.of(passenger));
    }
    private void tripIdExists() {
        passengerExists();
        when(reservationRepository.findTrajetIdByReservationId(20L)).thenReturn(Optional.of(10L));
    }
    private void tripExists() {
        tripIdExists(); when(trajetRepository.findByIdForReservation(10L)).thenReturn(Optional.of(trip));
    }
    private void allExist() {
        tripExists(); when(reservationRepository.findByIdForCancellation(20L, 10L)).thenReturn(Optional.of(reservation));
    }
    private void unchanged() {
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(trip.getStatus()).isEqualTo(StatutTrajet.COMPLET);
        verify(reservationRepository, never()).flush();
        verify(reservationRepository, never()).sumPlacesByTrajetIdAndStatut(any(), any());
    }

    @Test void cancelsThreeDaysBeforeDepartureReopensFullTripAndPreservesHistoryFields() {
        allExist();
        when(reservationRepository.sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(2L);
        service.cancel(1L, 20L, 1L);
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(trip.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
        assertThat(reservation.getNombrePlaces()).isEqualTo(2);
        assertThat(reservation.getMontantTotal()).isEqualByComparingTo("160.00");
        assertThat(reservation.getDateReservation()).isEqualTo(NOW.minusDays(1));
        var order = inOrder(reservationRepository, trajetRepository);
        order.verify(reservationRepository).findTrajetIdByReservationId(20L);
        order.verify(trajetRepository).findByIdForReservation(10L);
        order.verify(reservationRepository).findByIdForCancellation(20L, 10L);
        order.verify(reservationRepository).flush();
        order.verify(reservationRepository).sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE);
    }
    @Test void keepsPublishedTripPublished() {
        allExist(); trip.setStatus(StatutTrajet.PUBLIE);
        service.cancel(1L, 20L, 1L);
        assertThat(trip.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.ANNULEE);
    }
    @Test void acceptsExactly24HoursBeforeDeparture() {
        allExist(); trip.setDepartureTime(NOW.plusHours(24));
        service.cancel(1L, 20L, 1L);
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.ANNULEE);
    }
    @ParameterizedTest @ValueSource(longs = {23, 2, -1})
    void refusesLateOrPastDeparture(long hours) {
        allExist(); trip.setDepartureTime(NOW.plusHours(hours));
        assertThatThrownBy(() -> service.cancel(1L, 20L, 1L)).isInstanceOf(BusinessException.class);
        unchanged();
    }
    @Test void refusesOneNanosecondAfterDeadline() {
        allExist(); trip.setDepartureTime(NOW.plusHours(24).minusNanos(1));
        assertThatThrownBy(() -> service.cancel(1L, 20L, 1L)).isInstanceOf(BusinessException.class);
        unchanged();
    }
    @Test void refusesAnotherActorBeforeLockingAnything() {
        passengerExists();
        assertThatThrownBy(() -> service.cancel(1L, 20L, 2L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(reservationRepository, trajetRepository);
    }
    @Test void refusesReservationOwnedByAnotherPassenger() {
        allExist(); Passager other = new Passager(); other.setId(2L); reservation.setPassager(other);
        assertThatThrownBy(() -> service.cancel(1L, 20L, 1L)).isInstanceOf(ForbiddenException.class);
        unchanged();
    }
    @Test void refusesAlreadyCancelledWithoutReleasingPlacesAgain() {
        allExist(); reservation.setStatut(StatutReservation.ANNULEE);
        assertThatThrownBy(() -> service.cancel(1L, 20L, 1L)).isInstanceOf(BusinessException.class);
        assertThat(trip.getStatus()).isEqualTo(StatutTrajet.COMPLET);
        verify(reservationRepository, never()).flush();
        verify(reservationRepository, never()).sumPlacesByTrajetIdAndStatut(any(), any());
    }
    @ParameterizedTest @EnumSource(value = StatutTrajet.class, names = {"ANNULE", "TERMINE"})
    void refusesFinalTripWithoutReopeningIt(StatutTrajet status) {
        allExist(); trip.setStatus(status);
        assertThatThrownBy(() -> service.cancel(1L, 20L, 1L)).isInstanceOf(BusinessException.class);
        assertThat(trip.getStatus()).isEqualTo(status);
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        verify(reservationRepository, never()).flush();
    }
    @Test void refusesMissingPassenger() {
        assertThatThrownBy(() -> service.cancel(1L, 20L, 1L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(reservationRepository, trajetRepository);
    }
    @Test void refusesMissingReservation() {
        passengerExists();
        assertThatThrownBy(() -> service.cancel(1L, 20L, 1L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(trajetRepository);
    }
    @Test void refusesMissingTrip() {
        tripIdExists();
        assertThatThrownBy(() -> service.cancel(1L, 20L, 1L)).isInstanceOf(ResourceNotFoundException.class);
        verify(reservationRepository, never()).findByIdForCancellation(any(), any());
    }
    @Test void refusesReservationMissingAtReloadAfterLock() {
        tripExists();
        assertThatThrownBy(() -> service.cancel(1L, 20L, 1L)).isInstanceOf(ResourceNotFoundException.class);
        unchanged();
    }
}
