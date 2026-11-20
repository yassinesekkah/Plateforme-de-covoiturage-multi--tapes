package ma.youcode.covoiturage.validation;

import ma.youcode.covoiturage.dto.trajet.TrajetSearchRequest;
import ma.youcode.covoiturage.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TrajetSearchValidator {

    public void validate(TrajetSearchRequest request) {
        if (request == null) {
            throw new BadRequestException("Les criteres de recherche sont obligatoires");
        }
        if (request.departureCity() == null || request.departureCity().isBlank()
                || request.arrivalCity() == null || request.arrivalCity().isBlank()) {
            throw new BadRequestException("Les villes de depart et d'arrivee sont obligatoires");
        }
        if (request.departureCity().length() > 255 || request.arrivalCity().length() > 255) {
            throw new BadRequestException("Les villes ne doivent pas depasser 255 caracteres");
        }
        if (request.date() == null) {
            throw new BadRequestException("La date de recherche est obligatoire");
        }
        if (request.seats() == null || request.seats() < 1) {
            throw new BadRequestException("Il faut rechercher au moins une place");
        }
        BigDecimal maxPrice = request.maxPrice();
        if (maxPrice != null && (maxPrice.signum() < 0 || maxPrice.scale() > 2
                || maxPrice.compareTo(new BigDecimal("9999999999.99")) > 0)) {
            throw new BadRequestException("Le prix maximum doit etre positif ou nul avec au maximum deux decimales et dix chiffres entiers");
        }
        if (request.page() < 0 || request.size() < 1 || request.size() > 100) {
            throw new BadRequestException("La page doit etre positive ou nulle et sa taille comprise entre 1 et 100");
        }
    }
}
