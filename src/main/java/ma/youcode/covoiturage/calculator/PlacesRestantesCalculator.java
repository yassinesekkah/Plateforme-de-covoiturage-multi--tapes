package ma.youcode.covoiturage.calculator;

import lombok.RequiredArgsConstructor;

import ma.youcode.covoiturage.entity.Trajet;
import ma.youcode.covoiturage.enums.StatutReservation;
import ma.youcode.covoiturage.repository.ReservationRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlacesRestantesCalculator {

    private final ReservationRepository reservationRepository;

    public int calculate(Trajet trajet) {
        long reservedSeats = reservationRepository.sumPlacesByTrajetIdAndStatut(
                trajet.getId(), StatutReservation.CONFIRMEE);
        return Math.toIntExact(trajet.getOfferedSeats() - reservedSeats);
    }
}
