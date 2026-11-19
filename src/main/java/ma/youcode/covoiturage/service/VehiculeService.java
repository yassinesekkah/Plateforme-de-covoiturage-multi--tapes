package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.vehicule.*;
import java.util.List;

public interface VehiculeService {
    VehiculeResponse create(Long conducteurId, Long actorId, VehiculeCreateRequest request);
    VehiculeResponse getById(Long id);
    List<VehiculeResponse> getByConducteur(Long conducteurId);
    VehiculeResponse update(Long id, Long actorId, VehiculeUpdateRequest request);
    void delete(Long id, Long actorId);
}
