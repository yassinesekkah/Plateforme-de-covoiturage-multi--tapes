package ma.youcode.covoiturage.repository;

import jakarta.persistence.EntityManager;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.mapper.ReservationMapper;
import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.dto.reservation.ReservationResponse;
import ma.youcode.covoiturage.service.ReservationService;
import ma.youcode.covoiturage.service.TrajetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ReservationPersistenceTest {
    @Autowired private EntityManager entityManager;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationMapper reservationMapper;
    @Autowired private ReservationService reservationService;
    @Autowired private TrajetService trajetService;

    @Test void savesReservationWithPassengerTripAndTwoDistinctStops() {
        String suffix = UUID.randomUUID().toString();
        Conducteur conducteur = new Conducteur();
        conducteur.setFirstName("Test"); conducteur.setLastName("Reservation");
        conducteur.setPhone("0600000000"); conducteur.setEmail(suffix + "-c@example.com");
        conducteur.setLicenseNumber(suffix);
        entityManager.persist(conducteur);

        Passager passager = new Passager();
        passager.setFirstName("Salma"); passager.setLastName("Test");
        passager.setPhone("0600000000"); passager.setEmail(suffix + "-p@example.com");
        entityManager.persist(passager);

        Vehicule vehicule = new Vehicule();
        vehicule.setBrand("Dacia"); vehicule.setModel("Logan"); vehicule.setColor("Blanc");
        vehicule.setCapacity(4); vehicule.setRegistrationNumber(suffix);
        vehicule.setConducteur(conducteur);
        entityManager.persist(vehicule);

        LocalDateTime departure = LocalDateTime.now().plusDays(3);
        Trajet trajet = new Trajet();
        trajet.setConducteur(conducteur); trajet.setVehicule(vehicule);
        trajet.setDepartureCity("Rabat"); trajet.setArrivalCity("Marrakech");
        trajet.setDepartureTime(departure); trajet.setOfferedSeats(4);
        PointArret montee = point("Rabat", 0, departure, "0.00");
        PointArret descente = point("Marrakech", 1, departure.plusHours(4), "120.00");
        trajet.addPointArret(montee); trajet.addPointArret(descente);
        entityManager.persist(trajet);

        Reservation reservation = reservationMapper.toEntity(
                new ReservationCreateRequest(trajet.getId(), montee.getId(), descente.getId(), 2));
        // Le mapper ne charge pas les relations et ne decide pas les champs calcules.
        assertThat(reservation.getTrajet()).isNull();
        assertThat(reservation.getMontee()).isNull();
        assertThat(reservation.getDescente()).isNull();
        assertThat(reservation.getMontantTotal()).isNull();
        assertThat(reservation.getStatut()).isNull();
        reservation.setPassager(passager); reservation.setTrajet(trajet);
        reservation.setMontee(montee); reservation.setDescente(descente);
        reservation.setMontantTotal(new BigDecimal("240.00"));
        reservation.setDateReservation(LocalDateTime.now());
        reservation.setStatut(StatutReservation.CONFIRMEE);
        Long id = reservationRepository.saveAndFlush(reservation).getId();
        entityManager.clear();

        Reservation saved = reservationRepository.findById(id).orElseThrow();
        assertThat(saved.getPassager().getId()).isEqualTo(passager.getId());
        assertThat(saved.getTrajet().getId()).isEqualTo(trajet.getId());
        assertThat(saved.getMontee().getId()).isEqualTo(montee.getId());
        assertThat(saved.getDescente().getId()).isEqualTo(descente.getId());
        assertThat(saved.getMontee().getTrajet().getId()).isEqualTo(trajet.getId());
        assertThat(saved.getDescente().getTrajet().getId()).isEqualTo(trajet.getId());
        assertThat(saved.getNombrePlaces()).isEqualTo(2);
        assertThat(saved.getMontantTotal()).isEqualByComparingTo("240.00");
        assertThat(saved.getMontantTotal().scale()).isEqualTo(2);
        assertThat(saved.getDateReservation()).isNotNull();
        assertThat(saved.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(reservationService.getRemainingSeats(trajet.getId())).isEqualTo(2);
        assertThat(trajetService.getById(trajet.getId()).remainingSeats()).isEqualTo(2);

        ReservationResponse response = reservationMapper.toResponse(saved);
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.passagerId()).isEqualTo(passager.getId());
        assertThat(response.trajetId()).isEqualTo(trajet.getId());
        assertThat(response.monteeId()).isEqualTo(montee.getId());
        assertThat(response.descenteId()).isEqualTo(descente.getId());
        assertThat(response.nombrePlaces()).isEqualTo(2);
        assertThat(response.montantTotal()).isEqualByComparingTo("240.00");
        assertThat(response.dateReservation()).isEqualTo(saved.getDateReservation());
        assertThat(response.statut()).isEqualTo(StatutReservation.CONFIRMEE);

        saved.setStatut(StatutReservation.ANNULEE);
        reservationRepository.flush();
        entityManager.clear();
        assertThat(reservationRepository.findById(id).orElseThrow().getStatut())
                .isEqualTo(StatutReservation.ANNULEE);
        assertThat(reservationService.getRemainingSeats(trajet.getId())).isEqualTo(4);
        assertThat(trajetService.getById(trajet.getId()).remainingSeats()).isEqualTo(4);

        // Une reservation annulee n'empeche pas une nouvelle reservation confirmee.
        ReservationResponse created = reservationService.create(passager.getId(), passager.getId(),
                new ReservationCreateRequest(trajet.getId(), montee.getId(), descente.getId(), 4));
        entityManager.flush();
        entityManager.clear();
        assertThat(created.montantTotal()).isEqualByComparingTo("480.00");
        assertThat(created.statut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(reservationService.getRemainingSeats(trajet.getId())).isZero();
        var detail = trajetService.getById(trajet.getId());
        assertThat(detail.remainingSeats()).isZero();
        assertThat(detail.offeredSeats()).isEqualTo(4);
        assertThat(detail.status()).isEqualTo(StatutTrajet.COMPLET);
        assertThat(detail.pointsArret()).extracting(p -> p.city()).containsExactly("Rabat", "Marrakech");
        assertThat(entityManager.find(Trajet.class, trajet.getId()).getStatus())
                .isEqualTo(StatutTrajet.COMPLET);
    }

    private PointArret point(String city, int order, LocalDateTime time, String price) {
        PointArret point = new PointArret();
        point.setCity(city); point.setStopOrder(order);
        point.setPassageTime(time); point.setPriceFromDeparture(new BigDecimal(price));
        return point;
    }
}
