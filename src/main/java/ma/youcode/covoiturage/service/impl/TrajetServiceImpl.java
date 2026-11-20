package ma.youcode.covoiturage.service.impl;

import lombok.RequiredArgsConstructor;

import ma.youcode.covoiturage.builder.ParcoursBuilder;
import ma.youcode.covoiturage.calculator.PlacesRestantesCalculator;
import ma.youcode.covoiturage.dto.trajet.TrajetCreateRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetDetailResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import ma.youcode.covoiturage.entity.Conducteur;
import ma.youcode.covoiturage.entity.Trajet;
import ma.youcode.covoiturage.entity.Vehicule;
import ma.youcode.covoiturage.entity.Reservation;
import ma.youcode.covoiturage.enums.StatutTrajet;
import ma.youcode.covoiturage.enums.StatutReservation;
import ma.youcode.covoiturage.repository.ReservationRepository;
import ma.youcode.covoiturage.exception.BadRequestException;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.exception.ForbiddenException;
import ma.youcode.covoiturage.exception.ResourceNotFoundException;
import ma.youcode.covoiturage.mapper.TrajetMapper;
import ma.youcode.covoiturage.repository.ConducteurRepository;
import ma.youcode.covoiturage.repository.TrajetRepository;
import ma.youcode.covoiturage.repository.VehiculeRepository;
import ma.youcode.covoiturage.service.TrajetService;
import ma.youcode.covoiturage.validation.ParcoursValidator;
import ma.youcode.covoiturage.validation.TrajetSearchValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrajetServiceImpl implements TrajetService {

    private static final Logger log = LoggerFactory.getLogger(TrajetServiceImpl.class);

    private final ConducteurRepository conducteurRepository;
    private final VehiculeRepository vehiculeRepository;
    private final TrajetRepository trajetRepository;
    private final TrajetMapper trajetMapper;
    private final ReservationRepository reservationRepository;
    private final Clock clock;
    private final ParcoursValidator parcoursValidator;
    private final ParcoursBuilder parcoursBuilder;
    private final PlacesRestantesCalculator placesRestantesCalculator;
    private final TrajetSearchValidator trajetSearchValidator;

    @Override
    @Transactional
    public TrajetResponse create(Long conducteurId, Long actorId, TrajetCreateRequest request) {
        Conducteur conducteur = conducteurRepository.findById(conducteurId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conducteur introuvable avec l'identifiant " + conducteurId));

        if (!conducteur.getId().equals(actorId)) {
            throw new ForbiddenException("Vous ne pouvez publier que vos propres trajets");
        }

        Vehicule vehicule = vehiculeRepository.findById(request.vehiculeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicule introuvable avec l'identifiant " + request.vehiculeId()));

        if (!vehicule.getConducteur().getId().equals(conducteurId)) {
            throw new ForbiddenException("Ce vehicule appartient a un autre conducteur");
        }

        validateSeats(request.offeredSeats(), vehicule.getCapacity());
        parcoursValidator.validate(request.pointsArret());

        Trajet trajet = trajetMapper.toEntity(request);
        trajet.setConducteur(conducteur);
        trajet.setVehicule(vehicule);
        trajet.setStatus(StatutTrajet.PUBLIE);

        parcoursBuilder.build(trajet, request.pointsArret());

        // La cascade sauvegarde les points avec le trajet, dans la meme transaction.
        Trajet savedTrajet = trajetRepository.save(trajet);
        log.info("Publication du trajet {} par le conducteur {}", savedTrajet.getId(), conducteurId);
        return trajetMapper.toResponse(savedTrajet);
    }

    @Override
    @Transactional(readOnly = true)
    public TrajetDetailResponse getById(Long id) {
        Trajet trajet = trajetRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trajet introuvable avec l'identifiant " + id));
        int remainingSeats = placesRestantesCalculator.calculate(trajet);
        return trajetMapper.toDetailResponse(trajet, remainingSeats);
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<TrajetSearchResult> search(TrajetSearchRequest request) {
        trajetSearchValidator.validate(request);

        String departureCity = request.departureCity().trim();
        String arrivalCity = request.arrivalCity().trim();
        LocalDateTime startDate = request.date().atStartOfDay();
        LocalDateTime endDate = request.date().plusDays(1).atStartOfDay();
        PageRequest pageable = PageRequest.of(request.page(), request.size());

        return trajetRepository.search(departureCity, arrivalCity,
                startDate, endDate, request.seats(), request.maxPrice(), pageable);
    }

    @Override
    @Transactional
    public void cancel(Long conducteurId, Long trajetId, Long actorId) {
        Conducteur conducteur = conducteurRepository.findById(conducteurId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conducteur introuvable avec l'identifiant " + conducteurId));
        if (!conducteur.getId().equals(actorId)) {
            throw new ForbiddenException("Vous ne pouvez annuler que vos propres trajets");
        }

        Trajet trajet = trajetRepository.findByIdForReservation(trajetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trajet introuvable avec l'identifiant " + trajetId));
        if (!trajet.getConducteur().getId().equals(conducteurId)) {
            throw new ForbiddenException("Ce trajet appartient a un autre conducteur");
        }
        if (trajet.getStatus() != StatutTrajet.PUBLIE && trajet.getStatus() != StatutTrajet.COMPLET) {
            throw new BusinessException("Seul un trajet PUBLIE ou COMPLET peut etre annule");
        }

        List<Reservation> reservations = reservationRepository.findByTrajetIdAndStatut(
                trajetId, StatutReservation.CONFIRMEE);
        for (Reservation reservation : reservations) {
            reservation.setStatut(StatutReservation.ANNULEE);
        }
        trajet.setStatus(StatutTrajet.ANNULE);
        log.info("Annulation du trajet {} par le conducteur {} avec {} reservation(s) annulee(s)",
                trajetId, conducteurId, reservations.size());
    }

    @Override
    @Transactional
    public void finish(Long conducteurId, Long trajetId, Long actorId) {
        Conducteur conducteur = conducteurRepository.findById(conducteurId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conducteur introuvable avec l'identifiant " + conducteurId));
        if (!conducteur.getId().equals(actorId)) {
            throw new ForbiddenException("Vous ne pouvez terminer que vos propres trajets");
        }

        Trajet trajet = trajetRepository.findByIdForReservation(trajetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trajet introuvable avec l'identifiant " + trajetId));
        if (!trajet.getConducteur().getId().equals(conducteurId)) {
            throw new ForbiddenException("Ce trajet appartient a un autre conducteur");
        }
        if (trajet.getStatus() != StatutTrajet.PUBLIE && trajet.getStatus() != StatutTrajet.COMPLET) {
            throw new BusinessException("Seul un trajet PUBLIE ou COMPLET peut etre termine");
        }
        if (!LocalDateTime.now(clock).isAfter(trajet.getDepartureTime())) {
            throw new BusinessException("Le trajet ne peut etre termine qu'apres l'heure de depart");
        }

        trajet.setStatus(StatutTrajet.TERMINE);
        log.info("Fin du trajet {} par le conducteur {}", trajetId, conducteurId);
    }

    private void validateSeats(Integer offeredSeats, Integer capacity) {
        if (offeredSeats == null || offeredSeats < 1) {
            throw new BadRequestException("Il faut proposer au moins une place");
        }
        if (offeredSeats > capacity) {
            throw new BadRequestException("Le nombre de places ne doit pas depasser " + capacity);
        }
    }
}
