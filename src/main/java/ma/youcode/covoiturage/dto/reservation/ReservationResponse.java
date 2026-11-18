package ma.youcode.covoiturage.dto.reservation;

import ma.youcode.covoiturage.enums.StatutReservation;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long passagerId,
        Long trajetId,
        Long monteeId,
        Long descenteId,
        Integer nombrePlaces,
        BigDecimal montantTotal,
        LocalDateTime dateReservation,
        StatutReservation statut
) {
}
