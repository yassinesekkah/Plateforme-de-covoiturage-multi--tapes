package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.repository.*;
import ma.youcode.covoiturage.service.impl.TrajetServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrajetCancellationServiceTest {
    @Mock private ConducteurRepository conducteurRepository;
    @Mock private TrajetRepository trajetRepository;
    @Mock private ReservationRepository reservationRepository;
    @InjectMocks private TrajetServiceImpl service;
    private Conducteur conducteur;
    private Trajet trip;

    @BeforeEach void prepare() {
        conducteur = new Conducteur(); conducteur.setId(1L);
        trip = new Trajet(); trip.setId(10L); trip.setConducteur(conducteur);
        trip.setDepartureTime(LocalDateTime.now().plusHours(2));
    }
    private void existingConducteur() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(conducteur));
    }
    private void existingTrip() {
        existingConducteur();
        when(trajetRepository.findByIdForReservation(10L)).thenReturn(Optional.of(trip));
    }
    @ParameterizedTest @EnumSource(value = StatutTrajet.class, names = {"PUBLIE", "COMPLET"})
    void cancelsTripAndItsConfirmedReservationsWithoutPassengerDeadline(StatutTrajet status) {
        existingTrip(); trip.setStatus(status);
        Reservation first = new Reservation(); first.setStatut(StatutReservation.CONFIRMEE);
        Reservation second = new Reservation(); second.setStatut(StatutReservation.CONFIRMEE);
        when(reservationRepository.findByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE))
                .thenReturn(List.of(first, second));
        service.cancel(1L, 10L, 1L);
        assertThat(trip.getStatus()).isEqualTo(StatutTrajet.ANNULE);
        assertThat(first.getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(second.getStatut()).isEqualTo(StatutReservation.ANNULEE);
        var order = inOrder(trajetRepository, reservationRepository);
        order.verify(trajetRepository).findByIdForReservation(10L);
        order.verify(reservationRepository).findByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE);
        verify(reservationRepository, never()).delete(any());
    }
    @Test void cancelsTripWithoutReservations() {
        existingTrip();
        when(reservationRepository.findByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(List.of());
        service.cancel(1L, 10L, 1L);
        assertThat(trip.getStatus()).isEqualTo(StatutTrajet.ANNULE);
    }
    @Test void rejectsMissingConducteur() {
        assertThatThrownBy(() -> service.cancel(1L, 10L, 1L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(trajetRepository, reservationRepository);
    }
    @Test void rejectsOtherActorBeforeLockingTrip() {
        existingConducteur();
        assertThatThrownBy(() -> service.cancel(1L, 10L, 2L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(trajetRepository, reservationRepository);
    }
    @Test void rejectsMissingTrip() {
        existingConducteur();
        assertThatThrownBy(() -> service.cancel(1L, 10L, 1L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(reservationRepository);
    }
    @Test void rejectsOtherOwner() {
        existingTrip(); Conducteur other = new Conducteur(); other.setId(2L); trip.setConducteur(other);
        assertThatThrownBy(() -> service.cancel(1L, 10L, 1L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(reservationRepository);
    }
    @ParameterizedTest @EnumSource(value = StatutTrajet.class, names = {"ANNULE", "TERMINE"})
    void refusesFinalStatusWithoutChangingReservations(StatutTrajet status) {
        existingTrip(); trip.setStatus(status);
        assertThatThrownBy(() -> service.cancel(1L, 10L, 1L)).isInstanceOf(BusinessException.class);
        assertThat(trip.getStatus()).isEqualTo(status);
        verifyNoInteractions(reservationRepository);
    }
}
