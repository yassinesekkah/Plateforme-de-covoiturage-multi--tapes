package ma.youcode.covoiturage.dto.trajet;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PointArretCreateRequest(
        @NotBlank(message = "La ville est obligatoire")
        @Size(max = 255, message = "La ville ne doit pas depasser 255 caracteres")
        String city,

        @NotNull(message = "La date et l'heure de passage sont obligatoires")
        LocalDateTime passageTime,

        @NotNull(message = "Le prix depuis le depart est obligatoire")
        @DecimalMin(value = "0.00", message = "Le prix ne doit pas etre negatif")
        @Digits(integer = 10, fraction = 2, message = "Le prix accepte au maximum 10 chiffres entiers et 2 decimales")
        BigDecimal priceFromDeparture
) {
}
