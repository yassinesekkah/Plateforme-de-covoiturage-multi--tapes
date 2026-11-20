package ma.youcode.covoiturage.validation;

import lombok.RequiredArgsConstructor;

import ma.youcode.covoiturage.validation.rules.RegleParcours;

import ma.youcode.covoiturage.dto.trajet.PointArretCreateRequest;
import ma.youcode.covoiturage.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ParcoursValidator {

    private final List<RegleParcours> regles;

    public void validate(List<PointArretCreateRequest> points) {
        if (points == null || points.size() < 2) {
            throw new BadRequestException("Le trajet doit contenir au moins deux points d'arret");
        }

        Set<String> cities = new HashSet<>();
        for (int i = 0; i < points.size(); i++) {
            PointArretCreateRequest point = points.get(i);
            if (point == null || point.city() == null || point.city().isBlank()
                    || point.passageTime() == null || point.priceFromDeparture() == null) {
                throw new BadRequestException("Chaque point doit contenir une ville, une date et un prix");
            }

            PointArretCreateRequest precedent = i == 0 ? null : points.get(i - 1);
            ContexteParcours contexte = new ContexteParcours(point, precedent, cities);
            for (RegleParcours regle : regles) {
                regle.validate(contexte);
            }
        }
    }
}
