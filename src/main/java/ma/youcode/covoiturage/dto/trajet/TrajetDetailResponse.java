package ma.youcode.covoiturage.dto.trajet;

import ma.youcode.covoiturage.dto.vehicule.VehiculeResponse;
import ma.youcode.covoiturage.enums.StatutTrajet;
import java.time.LocalDateTime;
import java.util.List;

public record TrajetDetailResponse(
        Long id,
        ConducteurTrajetResponse conducteur,
        VehiculeResponse vehicule,
        String departureCity,
        String arrivalCity,
        LocalDateTime departureTime,
        Integer offeredSeats,
        StatutTrajet status,
        List<PointArretResponse> pointsArret
) {
}
