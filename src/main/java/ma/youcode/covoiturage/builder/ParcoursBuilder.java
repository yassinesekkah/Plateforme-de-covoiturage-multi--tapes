package ma.youcode.covoiturage.builder;

import lombok.RequiredArgsConstructor;

import ma.youcode.covoiturage.dto.trajet.PointArretCreateRequest;
import ma.youcode.covoiturage.entity.PointArret;
import ma.youcode.covoiturage.entity.Trajet;
import ma.youcode.covoiturage.mapper.PointArretMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ParcoursBuilder {

    private final PointArretMapper pointArretMapper;

    // Les points sont deja verifies par ParcoursValidator avant cet appel.
    public void build(Trajet trajet, List<PointArretCreateRequest> points) {
        for (int i = 0; i < points.size(); i++) {
            PointArret point = pointArretMapper.toEntity(points.get(i));
            point.setCity(point.getCity().trim());
            point.setStopOrder(i);
            point.setPriceFromDeparture(point.getPriceFromDeparture().setScale(2));
            trajet.addPointArret(point);
        }

        PointArret first = trajet.getPointsArret().get(0);
        PointArret last = trajet.getPointsArret().get(trajet.getPointsArret().size() - 1);
        trajet.setDepartureCity(first.getCity());
        trajet.setArrivalCity(last.getCity());
        trajet.setDepartureTime(first.getPassageTime());
    }
}
