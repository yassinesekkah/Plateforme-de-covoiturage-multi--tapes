package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.trajet.*;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.enums.*;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.mapper.*;
import ma.youcode.covoiturage.repository.*;
import ma.youcode.covoiturage.service.impl.TrajetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrajetServiceTest {
    @Mock private ConducteurRepository conducteurRepository;
    @Mock private VehiculeRepository vehiculeRepository;
    @Mock private TrajetRepository trajetRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private TrajetMapper trajetMapper;
    @Mock private PointArretMapper pointMapper;
    private TrajetServiceImpl service;
    private Conducteur conducteur;
    private Vehicule vehicule;
    private Trajet trajet;
    private final LocalDateTime maintenant = LocalDateTime.of(2030, 1, 1, 16, 0);

    @BeforeEach
    void preparer() {
        Clock clock = Clock.fixed(maintenant.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new TrajetServiceImpl(conducteurRepository, vehiculeRepository,
                trajetRepository, trajetMapper, pointMapper, reservationRepository, clock);
        conducteur = new Conducteur();
        conducteur.setId(1L);
        vehicule = new Vehicule();
        vehicule.setId(2L);
        vehicule.setCapacity(4);
        vehicule.setConducteur(conducteur);
        trajet = new Trajet();
        trajet.setId(10L);
        trajet.setConducteur(conducteur);
        trajet.setOfferedSeats(3);
    }

    private TrajetCreateRequest publication(int places) {
        LocalDateTime depart = LocalDateTime.now().plusDays(3);
        return new TrajetCreateRequest(2L, places, List.of(
                new PointArretCreateRequest("Rabat", depart, BigDecimal.ZERO),
                new PointArretCreateRequest("Marrakech", depart.plusHours(4), new BigDecimal("120.00"))));
    }

    private void vehiculeExistant() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(conducteur));
        when(vehiculeRepository.findById(2L)).thenReturn(Optional.of(vehicule));
    }

    private void trajetExistant() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(conducteur));
        when(trajetRepository.findByIdForReservation(10L)).thenReturn(Optional.of(trajet));
    }

    @Test
    void publierTrajetAvecSesPoints() {
        vehiculeExistant();
        TrajetCreateRequest request = publication(3);
        when(trajetMapper.toEntity(request)).thenReturn(trajet);
        when(pointMapper.toEntity(any())).thenAnswer(call -> {
            PointArretCreateRequest dto = call.getArgument(0);
            PointArret point = new PointArret();
            point.setCity(dto.city());
            point.setPassageTime(dto.passageTime());
            point.setPriceFromDeparture(dto.priceFromDeparture());
            return point;
        });
        when(trajetRepository.save(trajet)).thenReturn(trajet);

        service.create(1L, 1L, request);

        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
        assertThat(trajet.getDepartureCity()).isEqualTo("Rabat");
        assertThat(trajet.getArrivalCity()).isEqualTo("Marrakech");
        assertThat(trajet.getPointsArret()).extracting(PointArret::getStopOrder).containsExactly(0, 1);
        assertThat(trajet.getPointsArret()).allSatisfy(p -> assertThat(p.getTrajet()).isSameAs(trajet));
        verify(trajetRepository).save(trajet);
    }

    @Test
    void refuserCinqPlacesPourUneCapaciteDeQuatre() {
        vehiculeExistant();
        assertThatThrownBy(() -> service.create(1L, 1L, publication(5)))
                .isInstanceOf(BadRequestException.class);
        verify(trajetRepository, never()).save(any());
    }

    @Test
    void refuserVehiculeAutreConducteur() {
        vehiculeExistant();
        Conducteur autre = new Conducteur();
        autre.setId(3L);
        vehicule.setConducteur(autre);
        assertThatThrownBy(() -> service.create(1L, 1L, publication(3)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void refuserParcoursAvecUnSeulPoint() {
        vehiculeExistant();
        TrajetCreateRequest request = new TrajetCreateRequest(2L, 3,
                List.of(publication(3).pointsArret().get(0)));
        assertThatThrownBy(() -> service.create(1L, 1L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void calculerPlacesRestantesDansDetail() {
        when(trajetRepository.findDetailById(10L)).thenReturn(Optional.of(trajet));
        when(reservationRepository.sumPlacesByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE)).thenReturn(1L);
        service.getById(10L);
        verify(trajetMapper).toDetailResponse(trajet, 2);
    }

    @Test
    void rechercherAvecDateEtPagination() {
        TrajetSearchRequest request = new TrajetSearchRequest("Casablanca", "Settat",
                maintenant.toLocalDate(), 1, new BigDecimal("30"), 0, 10);
        Slice<TrajetSearchResult> resultats = new SliceImpl<>(List.of());
        when(trajetRepository.search(eq("Casablanca"), eq("Settat"),
                eq(maintenant.toLocalDate().atStartOfDay()),
                eq(maintenant.toLocalDate().plusDays(1).atStartOfDay()),
                eq(1), eq(new BigDecimal("30")), any())).thenReturn(resultats);
        assertThat(service.search(request)).isSameAs(resultats);
    }

    @Test
    void annulerTrajetEtSesDeuxReservations() {
        trajetExistant();
        trajet.setStatus(StatutTrajet.COMPLET);
        Reservation salma = new Reservation();
        Reservation karim = new Reservation();
        salma.setStatut(StatutReservation.CONFIRMEE);
        karim.setStatut(StatutReservation.CONFIRMEE);
        when(reservationRepository.findByTrajetIdAndStatut(10L, StatutReservation.CONFIRMEE))
                .thenReturn(List.of(salma, karim));
        service.cancel(1L, 10L, 1L);
        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.ANNULE);
        assertThat(salma.getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(karim.getStatut()).isEqualTo(StatutReservation.ANNULEE);
    }

    @Test
    void terminerApresDepart() {
        trajetExistant();
        trajet.setDepartureTime(maintenant.minusHours(1));
        service.finish(1L, 10L, 1L);
        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.TERMINE);
    }

    @Test
    void refuserTerminaisonAvantDepart() {
        trajetExistant();
        trajet.setDepartureTime(maintenant.plusHours(1));
        assertThatThrownBy(() -> service.finish(1L, 10L, 1L))
                .isInstanceOf(BusinessException.class);
        assertThat(trajet.getStatus()).isEqualTo(StatutTrajet.PUBLIE);
    }
}
