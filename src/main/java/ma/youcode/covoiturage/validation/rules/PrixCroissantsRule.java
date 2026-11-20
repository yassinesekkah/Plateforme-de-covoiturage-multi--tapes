package ma.youcode.covoiturage.validation.rules;

import ma.youcode.covoiturage.validation.ContexteParcours;

import ma.youcode.covoiturage.exception.BadRequestException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(40)
public class PrixCroissantsRule implements RegleParcours {
    @Override
    public void validate(ContexteParcours contexte) {
        BigDecimal price = contexte.point().priceFromDeparture();
        if (contexte.precedent() == null) {
            if (price.compareTo(BigDecimal.ZERO) != 0) {
                throw new BadRequestException("Le prix du premier point doit etre egal a zero");
            }
        } else if (price.compareTo(contexte.precedent().priceFromDeparture()) <= 0) {
            throw new BadRequestException("Les prix depuis le depart doivent etre strictement croissants");
        }
    }
}
