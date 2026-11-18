package ma.youcode.covoiturage.dto.trajet;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TrajetSearchRequest(
        @NotBlank(message = "La ville de depart est obligatoire")
        @Size(max = 255, message = "La ville de depart ne doit pas depasser 255 caracteres")
        String departureCity,

        @NotBlank(message = "La ville d'arrivee est obligatoire")
        @Size(max = 255, message = "La ville d'arrivee ne doit pas depasser 255 caracteres")
        String arrivalCity,

        @NotNull(message = "La date de recherche est obligatoire")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,

        @NotNull(message = "Le nombre de places recherchees est obligatoire")
        @Min(value = 1, message = "Il faut rechercher au moins une place")
        Integer seats,

        @DecimalMin(value = "0.00", message = "Le prix maximum ne doit pas etre negatif")
        @Digits(integer = 10, fraction = 2, message = "Le prix maximum accepte 10 chiffres entiers et 2 decimales")
        BigDecimal maxPrice,

        @Min(value = 0, message = "Le numero de page ne doit pas etre negatif")
        Integer page,

        @Min(value = 1, message = "La taille de page doit etre au moins 1")
        @Max(value = 100, message = "La taille de page ne doit pas depasser 100")
        Integer size
) {
    public TrajetSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 10 : size;
    }
}
