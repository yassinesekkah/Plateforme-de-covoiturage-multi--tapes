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
class ReservationHistoryServiceTest {
    @Mock private PassagerRepository passagerRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationMapper mapper;
    @InjectMocks private ReservationServiceImpl service;

    private void existingPassenger() {
        Passager p = new Passager(); p.setId(1L);
        when(passagerRepository.findById(1L)).thenReturn(Optional.of(p));
    }
    private ReservationDetailResponse response(Long id, StatutReservation statut) {
        return new ReservationDetailResponse(id, 1L, null, null, null,
                1, new BigDecimal("40.00"), LocalDateTime.of(2030, 1, 1, 12, 0), statut);
    }

    @Test void returnsConfirmedAndCancelledInRepositoryOrder() {
        existingPassenger();
        Reservation newest = new Reservation(); Reservation oldest = new Reservation();
        var cancelled = response(2L, StatutReservation.ANNULEE);
        var confirmed = response(1L, StatutReservation.CONFIRMEE);
        when(reservationRepository.findHistoryByPassagerId(1L)).thenReturn(List.of(newest, oldest));
        when(mapper.toDetailResponse(newest)).thenReturn(cancelled);
        when(mapper.toDetailResponse(oldest)).thenReturn(confirmed);

        assertThat(service.getByPassager(1L, 1L)).containsExactly(cancelled, confirmed);
        verify(reservationRepository, never()).save(any());
    }
    @Test void returnsEmptyListForPassengerWithoutReservations() {
        existingPassenger();
        when(reservationRepository.findHistoryByPassagerId(1L)).thenReturn(List.of());
        assertThat(service.getByPassager(1L, 1L)).isEmpty();
        verifyNoInteractions(mapper);
    }
    @Test void refusesOtherActorBeforeReadingHistory() {
        existingPassenger();
        assertThatThrownBy(() -> service.getByPassager(1L, 2L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(reservationRepository, mapper);
    }
    @Test void refusesMissingPassenger() {
        assertThatThrownBy(() -> service.getByPassager(99L, 99L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(reservationRepository, mapper);
    }
}
