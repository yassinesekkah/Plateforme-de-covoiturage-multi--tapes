package ma.youcode.covoiturage.service.impl;

import ma.youcode.covoiturage.dto.trajet.PointArretCreateRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetCreateRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetDetailResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import ma.youcode.covoiturage.entity.Conducteur;
import ma.youcode.covoiturage.entity.PointArret;
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
import ma.youcode.covoiturage.mapper.PointArretMapper;
import ma.youcode.covoiturage.mapper.TrajetMapper;
import ma.youcode.covoiturage.repository.ConducteurRepository;
import ma.youcode.covoiturage.repository.TrajetRepository;
import ma.youcode.covoiturage.repository.VehiculeRepository;
import ma.youcode.covoiturage.service.TrajetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TrajetServiceImpl implements TrajetService {

    private static final Logger log = LoggerFactory.getLogger(TrajetServiceImpl.class);

    private final ConducteurRepository conducteurRepository;
    private final VehiculeRepository vehiculeRepository;
    private final TrajetRepository trajetRepository;
    private final TrajetMapper trajetMapper;
    private final PointArretMapper pointArretMapper;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public TrajetServiceImpl(ConducteurRepository conducteurRepository,
                            VehiculeRepository vehiculeRepository,
                            TrajetRepository trajetRepository,
                            TrajetMapper trajetMapper,
                            PointArretMapper pointArretMapper,
                            ReservationRepository reservationRepository,
                            Clock clock) {
        this.conducteurRepository = conducteurRepository;
        this.vehiculeRepository = vehiculeRepository;
        this.trajetRepository = trajetRepository;
        this.trajetMapper = trajetMapper;
        this.pointArretMapper = pointArretMapper;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

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
        validatePoints(request.pointsArret());

        Trajet trajet = trajetMapper.toEntity(request);
        trajet.setConducteur(conducteur);
        trajet.setVehicule(vehicule);
        trajet.setStatus(StatutTrajet.PUBLIE);

        List<PointArretCreateRequest> points = request.pointsArret();
        for (int i = 0; i < points.size(); i++) {
            PointArret point = pointArretMapper.toEntity(points.get(i));
            point.setCity(point.getCity().trim());
            point.setStopOrder(i);
            point.setPriceFromDeparture(point.getPriceFromDeparture().setScale(2));
            trajet.addPointArret(point);
        }

        PointArret first = trajet.getPointsArret().get(0);
        PointArret last = trajet.getPointsArret().get(trajet.getPointsArret().size() - 1);
        trajet.setDepartureCity(first.getCity());
        trajet.setArrivalCity(last.getCity());
        trajet.setDepartureTime(first.getPassageTime());

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
        long reservedSeats = reservationRepository.sumPlacesByTrajetIdAndStatut(
                trajet.getId(), StatutReservation.CONFIRMEE);
        int remainingSeats = Math.toIntExact(trajet.getOfferedSeats() - reservedSeats);
        return trajetMapper.toDetailResponse(trajet, remainingSeats);
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<TrajetSearchResult> search(TrajetSearchRequest request) {
        if (request == null) {
            throw new BadRequestException("Les criteres de recherche sont obligatoires");
        }
        if (request.departureCity() == null || request.departureCity().isBlank()
                || request.arrivalCity() == null || request.arrivalCity().isBlank()) {
            throw new BadRequestException("Les villes de depart et d'arrivee sont obligatoires");
        }
        if (request.departureCity().length() > 255 || request.arrivalCity().length() > 255) {
            throw new BadRequestException("Les villes ne doivent pas depasser 255 caracteres");
        }
        if (request.date() == null) {
            throw new BadRequestException("La date de recherche est obligatoire");
        }
        if (request.seats() == null || request.seats() < 1) {
            throw new BadRequestException("Il faut rechercher au moins une place");
        }
        BigDecimal maxPrice = request.maxPrice();
        if (maxPrice != null && (maxPrice.signum() < 0 || maxPrice.scale() > 2
                || maxPrice.compareTo(new BigDecimal("9999999999.99")) > 0)) {
            throw new BadRequestException("Le prix maximum doit etre positif ou nul avec au maximum deux decimales et dix chiffres entiers");
        }
        if (request.page() < 0 || request.size() < 1 || request.size() > 100) {
            throw new BadRequestException("La page doit etre positive ou nulle et sa taille comprise entre 1 et 100");
        }

        String departureCity = request.departureCity().trim();
        String arrivalCity = request.arrivalCity().trim();
        LocalDateTime startDate = request.date().atStartOfDay();
        LocalDateTime endDate = request.date().plusDays(1).atStartOfDay();
        PageRequest pageable = PageRequest.of(request.page(), request.size());

        return trajetRepository.search(departureCity, arrivalCity,
                startDate, endDate, request.seats(), maxPrice, pageable);
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

    private void validatePoints(List<PointArretCreateRequest> points) {
        if (points == null || points.size() < 2) {
            throw new BadRequestException("Le trajet doit contenir au moins deux points d'arret");
        }

        Set<String> cities = new HashSet<>();
        for (int i = 0; i < points.size(); i++) {
            PointArretCreateRequest point = points.get(i);
            if (point == null || point.city() == null || point.city().isBlank()
                    || point.passageTime() == null || point.priceFromDeparture() == null) {
                throw new BadRequestException("Chaque point doit contenir une ville, une date et un prix");
            }

            String city = point.city().trim().toLowerCase(Locale.ROOT);
            if (!cities.add(city)) {
                throw new BadRequestException("Une ville ne peut pas apparaitre deux fois sur le parcours");
            }

            BigDecimal price = point.priceFromDeparture();
            if (price.signum() < 0 || price.stripTrailingZeros().scale() > 2
                    || price.compareTo(new BigDecimal("9999999999.99")) > 0) {
                throw new BadRequestException("Le prix doit etre positif ou nul et accepte au maximum deux decimales et dix chiffres entiers");
            }

            if (i == 0) {
                if (!point.passageTime().isAfter(LocalDateTime.now())) {
                    throw new BadRequestException("Le depart doit etre dans le futur");
                }
                if (price.compareTo(BigDecimal.ZERO) != 0) {
                    throw new BadRequestException("Le prix du premier point doit etre egal a zero");
                }
            } else {
                PointArretCreateRequest previous = points.get(i - 1);
                if (!point.passageTime().isAfter(previous.passageTime())) {
                    throw new BadRequestException("Les heures de passage doivent etre strictement croissantes");
                }
                if (price.compareTo(previous.priceFromDeparture()) <= 0) {
                    throw new BadRequestException("Les prix depuis le depart doivent etre strictement croissants");
                }
            }
        }
    }
}
