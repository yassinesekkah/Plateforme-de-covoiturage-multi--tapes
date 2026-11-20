package ma.youcode.covoiturage.service.impl;

import lombok.RequiredArgsConstructor;

import ma.youcode.covoiturage.calculator.PlacesRestantesCalculator;
import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.dto.reservation.ReservationResponse;
import ma.youcode.covoiturage.dto.reservation.ReservationDetailResponse;
import java.util.List;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.mapper.ReservationMapper;
import ma.youcode.covoiturage.repository.*;
import ma.youcode.covoiturage.service.ReservationService;
import ma.youcode.covoiturage.validation.ReservationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);
    private final PassagerRepository passagerRepository;
    private final TrajetRepository trajetRepository;
    private final PointArretRepository pointArretRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;
    private final ConducteurRepository conducteurRepository;
    private final PlacesRestantesCalculator placesRestantesCalculator;
    private final ReservationValidator reservationValidator;
    private final Clock clock;

    @Override
    @Transactional
    public ReservationResponse create(Long passagerId, Long actorId, ReservationCreateRequest request) {
        // 1. Charger le passager et verifier qu'il reserve pour son propre profil.
        Passager passager = passagerRepository.findById(passagerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passager introuvable avec l'identifiant " + passagerId));
        reservationValidator.validateOwner(passager.getId(), actorId,
                "Vous ne pouvez reserver que pour votre propre profil");

        // 2. Verrouiller le trajet et verifier son statut et le nombre de places demande.
        // Le verrou reste actif jusqu'au commit ou rollback de cette transaction.
        Trajet trajet = trajetRepository.findByIdForReservation(request.trajetId())
                .orElseThrow(() -> new ResourceNotFoundException("Trajet introuvable avec l'identifiant " + request.trajetId()));
        reservationValidator.validateCreation(trajet, request.nombrePlaces());

        // 3. Charger les points et verifier qu'ils appartiennent au trajet, dans le bon ordre.
        PointArret montee = pointArretRepository.findById(request.monteeId())
                .orElseThrow(() -> new ResourceNotFoundException("Point de montee introuvable"));
        PointArret descente = pointArretRepository.findById(request.descenteId())
                .orElseThrow(() -> new ResourceNotFoundException("Point de descente introuvable"));
        reservationValidator.validatePoints(trajet, montee, descente);

        // 4. Refuser une deuxieme reservation confirmee du meme passager sur ce trajet.
        reservationValidator.validateNoDuplicate(
                reservationRepository.existsByPassagerIdAndTrajetIdAndStatut(
                        passagerId, trajet.getId(), StatutReservation.CONFIRMEE));

        // 5. Calculer les places restantes et verifier qu'elles sont suffisantes.
        int remainingSeats = placesRestantesCalculator.calculate(trajet);
        reservationValidator.validateAvailableSeats(request.nombrePlaces(), remainingSeats);

        // 6. Calculer le montant, puis construire et sauvegarder la reservation.
        BigDecimal montantTotal = calculateAmount(montee, descente, request.nombrePlaces());
        Reservation reservation = buildReservation(request, passager, trajet, montee, descente, montantTotal);
        Reservation saved = reservationRepository.save(reservation);

        // 7. Passer le trajet a COMPLET si cette reservation prend les dernieres places.
        if (remainingSeats - request.nombrePlaces() == 0) {
            trajet.setStatus(StatutTrajet.COMPLET);
        }
        log.info("Creation de la reservation {} du passager {} sur le trajet {}", saved.getId(), passagerId, trajet.getId());

        // 8. Convertir la reservation sauvegardee en reponse pour le client.
        return reservationMapper.toResponse(saved);
    }

    private BigDecimal calculateAmount(PointArret montee, PointArret descente, Integer nombrePlaces) {
        // Prix du segment par place, multiplie par le nombre de places reservees.
        BigDecimal prixParPlace = descente.getPriceFromDeparture().subtract(montee.getPriceFromDeparture());
        return prixParPlace.multiply(BigDecimal.valueOf(nombrePlaces)).setScale(2);
    }

    private Reservation buildReservation(ReservationCreateRequest request, Passager passager,
                                         Trajet trajet, PointArret montee, PointArret descente,
                                         BigDecimal montantTotal) {
        // Le mapper copie la demande ; on complete les relations et les valeurs gerees par le serveur.
        Reservation reservation = reservationMapper.toEntity(request);
        reservation.setPassager(passager);
        reservation.setTrajet(trajet);
        reservation.setMontee(montee);
        reservation.setDescente(descente);
        reservation.setMontantTotal(montantTotal);
        reservation.setDateReservation(LocalDateTime.now(clock));
        reservation.setStatut(StatutReservation.CONFIRMEE);
        return reservation;
    }

    @Override
    @Transactional(readOnly = true)
    public int getRemainingSeats(Long trajetId) {
        Trajet trajet = trajetRepository.findById(trajetId)
                .orElseThrow(() -> new ResourceNotFoundException("Trajet introuvable avec l'identifiant " + trajetId));
        return placesRestantesCalculator.calculate(trajet);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationDetailResponse> getByPassager(Long passagerId, Long actorId) {
        Passager passager = passagerRepository.findById(passagerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Passager introuvable avec l'identifiant " + passagerId));
        reservationValidator.validateOwner(passager.getId(), actorId,
                "Vous ne pouvez consulter que vos propres reservations");
        return reservationRepository.findHistoryByPassagerId(passagerId)
                .stream()
                .map(reservationMapper::toDetailResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationDetailResponse> getByTrajetForConducteur(
            Long conducteurId, Long trajetId, Long actorId) {
        Conducteur conducteur = conducteurRepository.findById(conducteurId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conducteur introuvable avec l'identifiant " + conducteurId));
        reservationValidator.validateOwner(conducteur.getId(), actorId,
                "Vous ne pouvez consulter que vos propres trajets");
        Trajet trajet = trajetRepository.findById(trajetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trajet introuvable avec l'identifiant " + trajetId));
        reservationValidator.validateOwner(trajet.getConducteur().getId(), conducteurId,
                "Ce trajet appartient a un autre conducteur");
        return reservationRepository.findForConducteurByTrajetId(trajetId, conducteurId)
                .stream()
                .map(reservationMapper::toDetailResponse)
                .toList();
    }

    @Override
    @Transactional
    public void cancel(Long passagerId, Long reservationId, Long actorId) {
        Passager passager = passagerRepository.findById(passagerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Passager introuvable avec l'identifiant " + passagerId));
        reservationValidator.validateOwner(passager.getId(), actorId,
                "Vous ne pouvez annuler que vos propres reservations");

        Long trajetId = reservationRepository.findTrajetIdByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation introuvable avec l'identifiant " + reservationId));
        Trajet trajet = trajetRepository.findByIdForReservation(trajetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trajet introuvable avec l'identifiant " + trajetId));
        Reservation reservation = reservationRepository.findByIdForCancellation(reservationId, trajetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation introuvable avec l'identifiant " + reservationId));

        reservationValidator.validateCancellation(reservation, trajet, passagerId);

        reservation.setStatut(StatutReservation.ANNULEE);
        // Envoyer le changement a la base avant de recalculer les places ; ce n'est pas un commit.
        reservationRepository.flush();
        int remainingSeats = placesRestantesCalculator.calculate(trajet);
        if (trajet.getStatus() == StatutTrajet.COMPLET && remainingSeats > 0) {
            trajet.setStatus(StatutTrajet.PUBLIE);
        }
        log.info("Annulation de la reservation {} du passager {} sur le trajet {}",
                reservationId, passagerId, trajetId);
    }

}
