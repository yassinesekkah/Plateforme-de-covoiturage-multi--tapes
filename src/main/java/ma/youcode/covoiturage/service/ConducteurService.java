package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.conducteur.ConducteurCreateRequest;
import ma.youcode.covoiturage.dto.conducteur.ConducteurResponse;
import ma.youcode.covoiturage.dto.conducteur.ConducteurUpdateRequest;

import java.util.List;

public interface ConducteurService {

    ConducteurResponse create(ConducteurCreateRequest request);

    ConducteurResponse getById(Long id);

    List<ConducteurResponse> getAll();

    List<ConducteurResponse> search(String keyword);

    ConducteurResponse update(Long id, Long actorId, ConducteurUpdateRequest request);
}