package ma.youcode.covoiturage.service.impl;

import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.dto.reservation.ReservationResponse;
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

@Service
public class ReservationServiceImpl implements ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);
    private final PassagerRepository passagerRepository;
    private final TrajetRepository trajetRepository;
    private final PointArretRepository pointArretRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;

    public ReservationServiceImpl(PassagerRepository passagerRepository,
                                  TrajetRepository trajetRepository,
                                  PointArretRepository pointArretRepository,
                                  ReservationRepository reservationRepository,
                                  ReservationMapper reservationMapper) {
        this.passagerRepository = passagerRepository;
        this.trajetRepository = trajetRepository;
        this.pointArretRepository = pointArretRepository;
        this.reservationRepository = reservationRepository;
        this.reservationMapper = reservationMapper;
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

    private int calculateRemainingSeats(Trajet trajet) {
        long reservedSeats = reservationRepository.sumPlacesByTrajetIdAndStatut(trajet.getId(), StatutReservation.CONFIRMEE);
        return Math.toIntExact(trajet.getOfferedSeats() - reservedSeats);
    }
}
