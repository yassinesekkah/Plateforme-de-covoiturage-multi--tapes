package ma.youcode.covoiturage.dto.trajet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Un resultat correspond au segment recherche dans un trajet, pas au parcours entier.
public record TrajetSearchResult(
        Long trajetId,
        LocalDateTime departureTime,
        Long monteeId,
        String departureCity,
        LocalDateTime monteeTime,
        Long descenteId,
        String arrivalCity,
        LocalDateTime descenteTime,
        BigDecimal segmentPrice,
        Long remainingSeats
) {
}
