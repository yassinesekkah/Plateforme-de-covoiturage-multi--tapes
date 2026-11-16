package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.ConducteurCreateRequest;
import ma.youcode.covoiturage.dto.ConducteurResponse;

public interface ConducteurService {

    ConducteurResponse create(ConducteurCreateRequest request);
}