package ma.youcode.covoiturage.dto.trajet;

import ma.youcode.covoiturage.enums.StatutTrajet;
import java.time.LocalDateTime;
import java.util.List;

public record TrajetResponse(
        Long id,
        Long conducteurId,
        Long vehiculeId,
        String departureCity,
        String arrivalCity,
        LocalDateTime departureTime,
        Integer offeredSeats,
        StatutTrajet status,
        List<PointArretResponse> pointsArret
) {
}
