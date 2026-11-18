package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.reservation.ReservationDetailResponse;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.StatutReservation;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.mapper.ReservationMapper;
import ma.youcode.covoiturage.repository.*;
import ma.youcode.covoiturage.service.impl.ReservationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConducteurReservationServiceTest {
    @Mock private ConducteurRepository conducteurRepository;
    @Mock private TrajetRepository trajetRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationMapper mapper;
    @InjectMocks private ReservationServiceImpl service;

    private Conducteur existingConducteur() {
        Conducteur c = new Conducteur(); c.setId(1L);
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(c)); return c;
    }
    private void ownTrip() {
        Conducteur c = existingConducteur();
        Trajet t = new Trajet(); t.setId(10L); t.setConducteur(c);
        when(trajetRepository.findById(10L)).thenReturn(Optional.of(t));
    }
    @Test void returnsBothStatusesInRepositoryOrder() {
        ownTrip();
        Reservation first = new Reservation(); Reservation second = new Reservation();
        var time = LocalDateTime.of(2030, 1, 1, 12, 0);
        var cancelled = new ReservationDetailResponse(2L, 3L, null, null, null, 1,
                new BigDecimal("40.00"), time, StatutReservation.ANNULEE);
        var confirmed = new ReservationDetailResponse(1L, 4L, null, null, null, 2,
                new BigDecimal("80.00"), time.minusDays(1), StatutReservation.CONFIRMEE);
        when(reservationRepository.findForConducteurByTrajetId(10L, 1L)).thenReturn(List.of(first, second));
        when(mapper.toDetailResponse(first)).thenReturn(cancelled);
        when(mapper.toDetailResponse(second)).thenReturn(confirmed);
        assertThat(service.getByTrajetForConducteur(1L, 10L, 1L)).containsExactly(cancelled, confirmed);
        verify(reservationRepository, never()).save(any());
    }
    @Test void returnsEmptyListForOwnTripWithoutReservations() {
        ownTrip();
        when(reservationRepository.findForConducteurByTrajetId(10L, 1L)).thenReturn(List.of());
        assertThat(service.getByTrajetForConducteur(1L, 10L, 1L)).isEmpty();
        verifyNoInteractions(mapper);
    }
    @Test void refusesMissingConducteur() {
        assertThatThrownBy(() -> service.getByTrajetForConducteur(1L, 10L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(trajetRepository, reservationRepository, mapper);
    }
    @Test void refusesAnotherActorBeforeReadingTrip() {
        existingConducteur();
        assertThatThrownBy(() -> service.getByTrajetForConducteur(1L, 10L, 2L))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(trajetRepository, reservationRepository, mapper);
    }
    @Test void refusesMissingTrip() {
        existingConducteur();
        assertThatThrownBy(() -> service.getByTrajetForConducteur(1L, 10L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(reservationRepository, mapper);
    }
    @Test void refusesTripOwnedByAnotherConducteur() {
        existingConducteur();
        Conducteur other = new Conducteur(); other.setId(2L);
        Trajet t = new Trajet(); t.setId(10L); t.setConducteur(other);
        when(trajetRepository.findById(10L)).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.getByTrajetForConducteur(1L, 10L, 1L))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(reservationRepository, mapper);
    }
}
