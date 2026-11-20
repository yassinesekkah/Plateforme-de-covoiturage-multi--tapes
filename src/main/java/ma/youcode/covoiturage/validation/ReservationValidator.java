package ma.youcode.covoiturage.validation;

import lombok.RequiredArgsConstructor;
import ma.youcode.covoiturage.entity.PointArret;
import ma.youcode.covoiturage.entity.Reservation;
import ma.youcode.covoiturage.entity.Trajet;
import ma.youcode.covoiturage.enums.StatutReservation;
import ma.youcode.covoiturage.enums.StatutTrajet;
import ma.youcode.covoiturage.exception.BadRequestException;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.exception.ForbiddenException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ReservationValidator {

    private final Clock clock;

    public void validateOwner(Long ownerId, Long actorId, String message) {
        if (!ownerId.equals(actorId)) {
            throw new ForbiddenException(message);
        }
    }

    public void validateCreation(Trajet trajet, Integer nombrePlaces) {
        if (trajet.getStatus() != StatutTrajet.PUBLIE) {
            throw new BusinessException("Seul un trajet PUBLIE peut etre reserve");
        }
        if (nombrePlaces == null || nombrePlaces < 1) {
            throw new BadRequestException("Il faut reserver au moins une place");
        }
    }

    public void validatePoints(Trajet trajet, PointArret montee, PointArret descente) {
        if (!montee.getTrajet().getId().equals(trajet.getId())
                || !descente.getTrajet().getId().equals(trajet.getId())) {
            throw new BadRequestException("Les deux points doivent appartenir au trajet reserve");
        }
        if (montee.getStopOrder() >= descente.getStopOrder()) {
            throw new BadRequestException("La montee doit preceder la descente");
        }
    }

    public void validateNoDuplicate(boolean reservationConfirmeeExiste) {
        if (reservationConfirmeeExiste) {
            throw new BusinessException("Vous avez deja une reservation confirmee sur ce trajet");
        }
    }

    public void validateAvailableSeats(Integer nombrePlaces, int remainingSeats) {
        if (nombrePlaces > remainingSeats) {
            throw new BusinessException("Le nombre de places disponibles est insuffisant");
        }
    }

    public void validateCancellation(Reservation reservation, Trajet trajet, Long passagerId) {
        validateOwner(reservation.getPassager().getId(), passagerId,
                "Cette reservation appartient a un autre passager");
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
    }
}
