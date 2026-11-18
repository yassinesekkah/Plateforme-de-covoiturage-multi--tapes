package ma.youcode.covoiturage.dto.reservation;

import ma.youcode.covoiturage.enums.StatutTrajet;
import java.time.LocalDateTime;

public record TrajetReservationResponse(
        Long id,
        String departureCity,
        String arrivalCity,
        LocalDateTime departureTime,
        StatutTrajet status
) {
}
