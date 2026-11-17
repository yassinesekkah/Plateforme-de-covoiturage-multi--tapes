package ma.youcode.covoiturage.dto.vehicule;

import jakarta.validation.constraints.*;

public record VehiculeCreateRequest(
        @NotBlank(message = "La marque est obligatoire")
        @Size(max = 255, message = "La marque ne doit pas depasser 255 caracteres")
        String brand,
        @NotBlank(message = "Le modele est obligatoire")
        @Size(max = 255, message = "Le modele ne doit pas depasser 255 caracteres")
        String model,
        @NotBlank(message = "L'immatriculation est obligatoire")
        @Size(max = 255, message = "L'immatriculation ne doit pas depasser 255 caracteres")
        String registrationNumber,
        @NotBlank(message = "La couleur est obligatoire")
        @Size(max = 255, message = "La couleur ne doit pas depasser 255 caracteres")
        String color,
        @NotNull(message = "La capacite est obligatoire")
        @Min(value = 1, message = "La capacite doit etre au minimum de 1")
        @Max(value = 8, message = "La capacite doit etre au maximum de 8")
        Integer capacity
) {
}
