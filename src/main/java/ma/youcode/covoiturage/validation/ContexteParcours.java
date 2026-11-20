package ma.youcode.covoiturage.validation;

import ma.youcode.covoiturage.dto.trajet.PointArretCreateRequest;

import java.util.Set;

public record ContexteParcours(
        PointArretCreateRequest point,
        PointArretCreateRequest precedent,
        Set<String> villes
) {
}
