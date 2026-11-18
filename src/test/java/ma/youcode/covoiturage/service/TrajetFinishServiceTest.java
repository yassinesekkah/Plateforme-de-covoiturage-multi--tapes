package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.entity.Conducteur;
import ma.youcode.covoiturage.entity.Trajet;
import ma.youcode.covoiturage.enums.StatutTrajet;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.exception.ForbiddenException;
import ma.youcode.covoiturage.exception.ResourceNotFoundException;
import ma.youcode.covoiturage.repository.ConducteurRepository;
import ma.youcode.covoiturage.repository.ReservationRepository;
import ma.youcode.covoiturage.repository.TrajetRepository;
import ma.youcode.covoiturage.service.impl.TrajetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrajetFinishServiceTest {

    @Mock private ConducteurRepository conducteurRepository;
    @Mock private TrajetRepository trajetRepository;
    @Mock private ReservationRepository reservationRepository;
    private TrajetServiceImpl service;
    private Conducteur conducteur;
    private Trajet trajet;
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 17, 0);

    @BeforeEach
    void prepare() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new TrajetServiceImpl(conducteurRepository, null, trajetRepository,
                null, null, reservationRepository, clock);
        conducteur = new Conducteur();
        conducteur.setId(1L);
        trajet = new Trajet();
        trajet.setId(10L);
        trajet.setConducteur(conducteur);
        trajet.setStatus(StatutTrajet.PUBLIE);
        trajet.setDepartureTime(NOW.minusHours(1));
        trajet.setOfferedSeats(4);
    }

    private void existingConducteur() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(conducteur));
    }

    private void existingTrajet() {
        existingConducteur();
        when(trajetRepository.findByIdForReservation(10L)).thenReturn(Optional.of(trajet));
    }

    @ParameterizedTest
    @EnumSource(value = StatutTrajet.class, names = {"PUBLIE", "COMPLET"})
    void finishesAfterDepartureWithoutTouchingReservations(StatutTrajet status) {
        existingTrajet();
        trajet.setStatus(status);

        service.finish(1L, 10L, 1L);

        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.TERMINE);
        assertThat(trajet.getDepartureTime()).isEqualTo(NOW.minusHours(1));
        assertThat(trajet.getOfferedSeats()).isEqualTo(4);
        assertThat(trajet.getConducteur()).isSameAs(conducteur);
        verify(trajetRepository).findByIdForReservation(10L);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void allowsFinishingOneNanosecondAfterDeparture() {
        existingTrajet();
        trajet.setDepartureTime(NOW.minusNanos(1));

        service.finish(1L, 10L, 1L);

        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.TERMINE);
    }

    @ParameterizedTest
    @EnumSource(value = StatutTrajet.class, names = {"PUBLIE", "COMPLET"})
    void refusesBeforeDeparture(StatutTrajet status) {
        existingTrajet();
        trajet.setStatus(status);
        trajet.setDepartureTime(NOW.plusHours(1));

        assertThatThrownBy(() -> service.finish(1L, 10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Le trajet ne peut etre termine qu'apres l'heure de depart");

        assertThat(trajet.getStatus()).isEqualTo(status);
        verifyNoInteractions(reservationRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void refusesAtDepartureOrJustBefore(long nanosUntilDeparture) {
        existingTrajet();
        trajet.setDepartureTime(NOW.plusNanos(nanosUntilDeparture));

        assertThatThrownBy(() -> service.finish(1L, 10L, 1L))
                .isInstanceOf(BusinessException.class);

        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
        verifyNoInteractions(reservationRepository);
    }

    @ParameterizedTest
    @EnumSource(value = StatutTrajet.class, names = {"ANNULE", "TERMINE"})
    void refusesFinalStatuses(StatutTrajet status) {
        existingTrajet();
        trajet.setStatus(status);

        assertThatThrownBy(() -> service.finish(1L, 10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Seul un trajet PUBLIE ou COMPLET peut etre termine");

        assertThat(trajet.getStatus()).isEqualTo(status);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void refusesMissingConducteur() {
        assertThatThrownBy(() -> service.finish(1L, 10L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(trajetRepository, reservationRepository);
    }

    @Test
    void refusesOtherActorBeforeLockingTrajet() {
        existingConducteur();

        assertThatThrownBy(() -> service.finish(1L, 10L, 2L))
                .isInstanceOf(ForbiddenException.class);

        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
        verifyNoInteractions(trajetRepository, reservationRepository);
    }

    @Test
    void refusesMissingTrajet() {
        existingConducteur();

        assertThatThrownBy(() -> service.finish(1L, 10L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(reservationRepository);
    }

    @Test
    void refusesTrajetOwnedByAnotherConducteur() {
        existingTrajet();
        Conducteur other = new Conducteur();
        other.setId(2L);
        trajet.setConducteur(other);

        assertThatThrownBy(() -> service.finish(1L, 10L, 1L))
                .isInstanceOf(ForbiddenException.class);

        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
        verifyNoInteractions(reservationRepository);
    }
}
