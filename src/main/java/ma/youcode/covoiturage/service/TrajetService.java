package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.trajet.TrajetCreateRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetResponse;

public interface TrajetService {

    TrajetResponse create(Long conducteurId, Long actorId, TrajetCreateRequest request);
}
