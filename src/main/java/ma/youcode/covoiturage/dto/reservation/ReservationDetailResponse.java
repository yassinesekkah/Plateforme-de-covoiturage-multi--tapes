package ma.youcode.covoiturage.dto.reservation;

import ma.youcode.covoiturage.dto.trajet.PointArretResponse;
import ma.youcode.covoiturage.enums.StatutReservation;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationDetailResponse(
        Long id,
        Long passagerId,
        TrajetReservationResponse trajet,
        PointArretResponse montee,
        PointArretResponse descente,
        Integer nombrePlaces,
        BigDecimal montantTotal,
        LocalDateTime dateReservation,
        StatutReservation statut
) {
}
