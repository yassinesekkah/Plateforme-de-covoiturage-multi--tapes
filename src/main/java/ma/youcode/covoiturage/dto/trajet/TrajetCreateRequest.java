package ma.youcode.covoiturage.dto.trajet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record TrajetCreateRequest(
        @NotNull(message = "Le vehicule est obligatoire")
        @Positive(message = "L'identifiant du vehicule doit etre positif")
        Long vehiculeId,

        @NotNull(message = "Le nombre de places proposees est obligatoire")
        @Min(value = 1, message = "Il faut proposer au moins une place")
        Integer offeredSeats,

        @NotNull(message = "Le parcours est obligatoire")
        @Size(min = 2, message = "Le trajet doit contenir au moins deux points d'arret")
        @Valid
        List<@NotNull(message = "Un point d'arret ne peut pas etre null") PointArretCreateRequest> pointsArret
) {
}
