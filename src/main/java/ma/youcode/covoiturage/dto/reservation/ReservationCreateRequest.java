package ma.youcode.covoiturage.dto.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReservationCreateRequest(
        @NotNull(message = "Le trajet est obligatoire")
        @Positive(message = "L'identifiant du trajet doit etre positif")
        Long trajetId,

        @NotNull(message = "Le point de montee est obligatoire")
        @Positive(message = "L'identifiant du point de montee doit etre positif")
        Long monteeId,

        @NotNull(message = "Le point de descente est obligatoire")
        @Positive(message = "L'identifiant du point de descente doit etre positif")
        Long descenteId,

        @NotNull(message = "Le nombre de places est obligatoire")
        @Min(value = 1, message = "Il faut reserver au moins une place")
        Integer nombrePlaces
) {
}
