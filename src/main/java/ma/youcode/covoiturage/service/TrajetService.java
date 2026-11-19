package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.trajet.TrajetCreateRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetDetailResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchResult;
import org.springframework.data.domain.Slice;

public interface TrajetService {

    TrajetResponse create(Long conducteurId, Long actorId, TrajetCreateRequest request);

    TrajetDetailResponse getById(Long id);

    Slice<TrajetSearchResult> search(TrajetSearchRequest request);

    void cancel(Long conducteurId, Long trajetId, Long actorId);

    void finish(Long conducteurId, Long trajetId, Long actorId);
}
