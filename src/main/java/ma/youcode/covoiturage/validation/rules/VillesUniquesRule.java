package ma.youcode.covoiturage.validation.rules;

import ma.youcode.covoiturage.validation.ContexteParcours;

import ma.youcode.covoiturage.exception.BadRequestException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Order(10)
public class VillesUniquesRule implements RegleParcours {
    @Override
    public void validate(ContexteParcours contexte) {
        String city = contexte.point().city().trim().toLowerCase(Locale.ROOT);
        if (!contexte.villes().add(city)) {
            throw new BadRequestException("Une ville ne peut pas apparaitre deux fois sur le parcours");
        }
    }
}
