package ma.youcode.covoiturage.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConducteurUpdateRequest(

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 255, message = "Le nom ne doit pas depasser 255 caracteres")
        String lastName,

        @NotBlank(message = "Le prenom est obligatoire")
        @Size(max = 255, message = "Le prenom ne doit pas depasser 255 caracteres")
        String firstName,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email est invalide")
        @Size(max = 255, message = "L'email ne doit pas depasser 255 caracteres")
        String email,

        @NotBlank(message = "Le telephone est obligatoire")
        @Size(max = 255, message = "Le telephone ne doit pas depasser 255 caracteres")
        String phone,

        @NotBlank(message = "Le numero de permis est obligatoire")
        @Size(max = 255, message = "Le numero de permis ne doit pas depasser 255 caracteres")
        String licenseNumber

) {
}