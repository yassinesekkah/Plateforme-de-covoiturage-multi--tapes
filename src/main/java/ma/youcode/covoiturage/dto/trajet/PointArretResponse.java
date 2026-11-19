package ma.youcode.covoiturage.dto.trajet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PointArretResponse(
        Long id,
        String city,
        Integer stopOrder,
        LocalDateTime passageTime,
        BigDecimal priceFromDeparture
) {
}
