package ma.youcode.covoiturage.service.impl;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;

@Service
public class ReservationServiceImpl implements ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);
    private final PassagerRepository passagerRepository;
    private final TrajetRepository trajetRepository;
    private final PointArretRepository pointArretRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;
    private final ConducteurRepository conducteurRepository;
    private final Clock clock;

    public ReservationServiceImpl(PassagerRepository passagerRepository,
                                  TrajetRepository trajetRepository,
                                  PointArretRepository pointArretRepository,
                                  ReservationRepository reservationRepository,
                                  ReservationMapper reservationMapper,
                                  ConducteurRepository conducteurRepository,
                                  Clock clock) {
        this.passagerRepository = passagerRepository;
        this.trajetRepository = trajetRepository;
        this.pointArretRepository = pointArretRepository;
        this.reservationRepository = reservationRepository;
        this.reservationMapper = reservationMapper;
        this.conducteurRepository = conducteurRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReservationResponse create(Long passagerId, Long actorId, ReservationCreateRequest request) {
        Passager passager = passagerRepository.findById(passagerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passager introuvable avec l'identifiant " + passagerId));
        if (!passager.getId().equals(actorId)) {
            throw new ForbiddenException("Vous ne pouvez reserver que pour votre propre profil");
        }

        // Le verrou reste actif jusqu'au commit ou rollback de cette transaction.
        Trajet trajet = trajetRepository.findByIdForReservation(request.trajetId())
                .orElseThrow(() -> new ResourceNotFoundException("Trajet introuvable avec l'identifiant " + request.trajetId()));
        if (trajet.getStatus() != StatutTrajet.PUBLIE) {
            throw new BusinessException("Seul un trajet PUBLIE peut etre reserve");
        }
        if (request.nombrePlaces() == null || request.nombrePlaces() < 1) {
            throw new BadRequestException("Il faut reserver au moins une place");
        }

        PointArret montee = pointArretRepository.findById(request.monteeId())
                .orElseThrow(() -> new ResourceNotFoundException("Point de montee introuvable"));
        PointArret descente = pointArretRepository.findById(request.descenteId())
                .orElseThrow(() -> new ResourceNotFoundException("Point de descente introuvable"));
        if (!montee.getTrajet().getId().equals(trajet.getId())
                || !descente.getTrajet().getId().equals(trajet.getId())) {
            throw new BadRequestException("Les deux points doivent appartenir au trajet reserve");
        }
        if (montee.getStopOrder() >= descente.getStopOrder()) {
            throw new BadRequestException("La montee doit preceder la descente");
        }
        if (reservationRepository.existsByPassagerIdAndTrajetIdAndStatut(
                passagerId, trajet.getId(), StatutReservation.CONFIRMEE)) {
            throw new BusinessException("Vous avez deja une reservation confirmee sur ce trajet");
        }

        int remainingSeats = calculateRemainingSeats(trajet);
        if (request.nombrePlaces() > remainingSeats) {
            throw new BusinessException("Le nombre de places disponibles est insuffisant");
        }
        BigDecimal prixParPlace = descente.getPriceFromDeparture().subtract(montee.getPriceFromDeparture());
        BigDecimal montantTotal = prixParPlace.multiply(BigDecimal.valueOf(request.nombrePlaces())).setScale(2);

        Reservation reservation = reservationMapper.toEntity(request);
        reservation.setPassager(passager);
        reservation.setTrajet(trajet);
        reservation.setMontee(montee);
        reservation.setDescente(descente);
        reservation.setMontantTotal(montantTotal);
        reservation.setDateReservation(LocalDateTime.now());
        reservation.setStatut(StatutReservation.CONFIRMEE);
        Reservation saved = reservationRepository.save(reservation);

        if (remainingSeats - request.nombrePlaces() == 0) {
            trajet.setStatus(StatutTrajet.COMPLET);
        }
        log.info("Creation de la reservation {} du passager {} sur le trajet {}", saved.getId(), passagerId, trajet.getId());
        return reservationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public int getRemainingSeats(Long trajetId) {
        Trajet trajet = trajetRepository.findById(trajetId)
                .orElseThrow(() -> new ResourceNotFoundException("Trajet introuvable avec l'identifiant " + trajetId));
        return calculateRemainingSeats(trajet);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationDetailResponse> getByPassager(Long passagerId, Long actorId) {
        Passager passager = passagerRepository.findById(passagerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Passager introuvable avec l'identifiant " + passagerId));
        if (!passager.getId().equals(actorId)) {
            throw new ForbiddenException("Vous ne pouvez consulter que vos propres reservations");
        }
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
        if (!conducteur.getId().equals(actorId)) {
            throw new ForbiddenException("Vous ne pouvez consulter que vos propres trajets");
        }
        Trajet trajet = trajetRepository.findById(trajetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trajet introuvable avec l'identifiant " + trajetId));
        if (!trajet.getConducteur().getId().equals(conducteurId)) {
            throw new ForbiddenException("Ce trajet appartient a un autre conducteur");
        }
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
        if (!passager.getId().equals(actorId)) {
            throw new ForbiddenException("Vous ne pouvez annuler que vos propres reservations");
        }

        Long trajetId = reservationRepository.findTrajetIdByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation introuvable avec l'identifiant " + reservationId));
        Trajet trajet = trajetRepository.findByIdForReservation(trajetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trajet introuvable avec l'identifiant " + trajetId));
        Reservation reservation = reservationRepository.findByIdForCancellation(reservationId, trajetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation introuvable avec l'identifiant " + reservationId));

        if (!reservation.getPassager().getId().equals(passagerId)) {
            throw new ForbiddenException("Cette reservation appartient a un autre passager");
        }
        if (reservation.getStatut() != StatutReservation.CONFIRMEE) {
            throw new BusinessException("Cette reservation est deja annulee");
        }
        if (trajet.getStatus() != StatutTrajet.PUBLIE && trajet.getStatus() != StatutTrajet.COMPLET) {
            throw new BusinessException("Un trajet ANNULE ou TERMINE ne permet plus d'annuler une reservation");
        }
        LocalDateTime limiteAnnulation = trajet.getDepartureTime().minusHours(24);
        if (LocalDateTime.now(clock).isAfter(limiteAnnulation)) {
            throw new BusinessException("L'annulation doit etre effectuee au moins 24 heures avant le depart du trajet");
        }

        reservation.setStatut(StatutReservation.ANNULEE);
        // Envoyer le changement a la base avant de recalculer les places ; ce n'est pas un commit.
        reservationRepository.flush();
        int remainingSeats = calculateRemainingSeats(trajet);
        if (trajet.getStatus() == StatutTrajet.COMPLET && remainingSeats > 0) {
            trajet.setStatus(StatutTrajet.PUBLIE);
        }
        log.info("Annulation de la reservation {} du passager {} sur le trajet {}",
                reservationId, passagerId, trajetId);
    }

    private int calculateRemainingSeats(Trajet trajet) {
        long reservedSeats = reservationRepository.sumPlacesByTrajetIdAndStatut(trajet.getId(), StatutReservation.CONFIRMEE);
        return Math.toIntExact(trajet.getOfferedSeats() - reservedSeats);
    }
}
