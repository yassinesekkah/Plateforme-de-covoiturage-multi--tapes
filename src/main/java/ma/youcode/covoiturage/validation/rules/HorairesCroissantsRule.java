package ma.youcode.covoiturage.validation.rules;

import lombok.RequiredArgsConstructor;
import ma.youcode.covoiturage.validation.ContexteParcours;

import ma.youcode.covoiturage.exception.BadRequestException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@Order(30)
@RequiredArgsConstructor
public class HorairesCroissantsRule implements RegleParcours {
    private final Clock clock;

    @Override
    public void validate(ContexteParcours contexte) {
        if (contexte.precedent() == null) {
            if (!contexte.point().passageTime().isAfter(LocalDateTime.now(clock))) {
                throw new BadRequestException("Le depart doit etre dans le futur");
            }
        } else if (!contexte.point().passageTime().isAfter(contexte.precedent().passageTime())) {
            throw new BadRequestException("Les heures de passage doivent etre strictement croissantes");
        }
    }
}
