package ma.youcode.covoiturage.validation.rules;

import ma.youcode.covoiturage.validation.ContexteParcours;

import ma.youcode.covoiturage.exception.BadRequestException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(20)
public class FormatPrixRule implements RegleParcours {
    @Override
    public void validate(ContexteParcours contexte) {
        BigDecimal price = contexte.point().priceFromDeparture();
        if (price.signum() < 0 || price.stripTrailingZeros().scale() > 2
                || price.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new BadRequestException("Le prix doit etre positif ou nul et accepte au maximum deux decimales et dix chiffres entiers");
        }
    }
}
