package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.calculator.PlacesRestantesCalculator;
import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.mapper.ReservationMapper;
import ma.youcode.covoiturage.repository.*;
import ma.youcode.covoiturage.service.impl.ReservationServiceImpl;
import ma.youcode.covoiturage.validation.ReservationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    @Mock private PassagerRepository passagerRepository;
    @Mock private TrajetRepository trajetRepository;
    @Mock private PointArretRepository pointRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ConducteurRepository conducteurRepository;
    private ReservationServiceImpl service;
    private Passager passager;
    private Trajet trajet;
    private PointArret montee;
    private PointArret descente;
    private Reservation reservation;
    private final LocalDateTime maintenant = LocalDateTime.of(2030, 1, 1, 12, 0);

    @BeforeEach
    void preparer() {
        Clock clock = Clock.fixed(maintenant.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ReservationServiceImpl(passagerRepository, trajetRepository, pointRepository,
                reservationRepository, Mappers.getMapper(ReservationMapper.class), conducteurRepository,
                new PlacesRestantesCalculator(reservationRepository), new ReservationValidator(clock), clock);
        passager = new Passager();
        passager.setId(1L);
        trajet = new Trajet();
        trajet.setId(10L);
        trajet.setOfferedSeats(3);
        trajet.setDepartureTime(maintenant.plusDays(3));
        montee = new PointArret();
        montee.setId(20L);
        montee.setTrajet(trajet);
        montee.setStopOrder(1);
        montee.setPriceFromDeparture(new BigDecimal("40.00"));
        descente = new PointArret();
        descente.setId(21L);
        descente.setTrajet(trajet);
        descente.setStopOrder(3);
        descente.setPriceFromDeparture(new BigDecimal("120.00"));
        reservation = new Reservation();
        reservation.setId(30L);
        reservation.setPassager(passager);
        reservation.setTrajet(trajet);
        reservation.setStatut(StatutReservation.CONFIRMEE);
        reservation.setNombrePlaces(2);
        reservation.setMontantTotal(new BigDecimal("160.00"));
    }

    private ReservationCreateRequest demande(int places) {
        return new ReservationCreateRequest(10L, 20L, 21L, places);
    }

    private void trajetExistant() {
        when(passagerRepository.findById(1L)).thenReturn(Optional.of(passager));
        when(trajetRepository.findByIdForReservation(10L)).thenReturn(Optional.of(trajet));
    }

    private void pointsExistants() {
        trajetExistant();
        when(pointRepository.findById(20L)).thenReturn(Optional.of(montee));
        when(pointRepository.findById(21L)).thenReturn(Optional.of(descente));
    }

    private void reservationExistante() {
        trajetExistant();
        when(reservationRepository.findTrajetIdByReservationId(30L)).thenReturn(Optional.of(10L));
        when(reservationRepository.findByIdForCancellation(30L, 10L)).thenReturn(Optional.of(reservation));
    }

    @Test
    void calculerCinquanteDhPourSalma() {
        pointsExistants();
        montee.setPriceFromDeparture(new BigDecimal("70.00"));
        when(reservationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.create(1L, 1L, demande(1));
        assertThat(response.montantTotal()).isEqualByComparingTo("50.00");
        assertThat(response.dateReservation()).isEqualTo(maintenant);
        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
    }

    @Test
    void calculerCentSoixanteDhEtRemplirTrajet() {
        pointsExistants();
        when(reservationRepository.sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(1L);
        when(reservationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.create(1L, 1L, demande(2)).montantTotal()).isEqualByComparingTo("160.00");
        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.COMPLET);
    }

    @Test
    void refuserTrajetComplet() {
        trajetExistant();
        trajet.setStatus(StatutTrajet.COMPLET);
        assertThatThrownBy(() -> service.create(1L, 1L, demande(1))).isInstanceOf(BusinessException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void refuserMonteeApresDescente() {
        pointsExistants();
        montee.setStopOrder(4);
        assertThatThrownBy(() -> service.create(1L, 1L, demande(1))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refuserPointAutreTrajet() {
        pointsExistants();
        Trajet autre = new Trajet();
        autre.setId(99L);
        montee.setTrajet(autre);
        assertThatThrownBy(() -> service.create(1L, 1L, demande(1))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refuserDeuxReservationsConfirmeesDuMemePassager() {
        pointsExistants();
        when(reservationRepository.existsByPassagerIdAndTrajetIdAndStatut(1L, 10L, StatutReservation.CONFIRMEE))
                .thenReturn(true);
        assertThatThrownBy(() -> service.create(1L, 1L, demande(1))).isInstanceOf(BusinessException.class);
    }

    @Test
    void refuserPlacesInsuffisantes() {
        pointsExistants();
        when(reservationRepository.sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(2L);
        assertThatThrownBy(() -> service.create(1L, 1L, demande(2))).isInstanceOf(BusinessException.class);
    }

    @Test
    void annulerTroisJoursAvantEtLibererDeuxPlaces() {
        reservationExistante();
        trajet.setStatus(StatutTrajet.COMPLET);
        when(reservationRepository.sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(1L);
        service.cancel(1L, 30L, 1L);
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(reservation.getMontantTotal()).isEqualByComparingTo("160.00");
        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
        when(trajetRepository.findById(10L)).thenReturn(Optional.of(trajet));
        assertThat(service.getRemainingSeats(10L)).isEqualTo(2);
    }

    @Test
    void refuserAnnulationDeuxHeuresAvantDepart() {
        reservationExistante();
        trajet.setDepartureTime(maintenant.plusHours(2));
        assertThatThrownBy(() -> service.cancel(1L, 30L, 1L)).isInstanceOf(BusinessException.class);
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
    }

    @Test
    void autoriserAnnulationExactementVingtQuatreHeuresAvant() {
        reservationExistante();
        trajet.setDepartureTime(maintenant.plusHours(24));
        service.cancel(1L, 30L, 1L);
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.ANNULEE);
    }

    @Test
    void refuserAnnulationParUnAutrePassager() {
        reservationExistante();
        Passager autre = new Passager();
        autre.setId(2L);
        reservation.setPassager(autre);
        assertThatThrownBy(() -> service.cancel(1L, 30L, 1L)).isInstanceOf(ForbiddenException.class);
    }
}
