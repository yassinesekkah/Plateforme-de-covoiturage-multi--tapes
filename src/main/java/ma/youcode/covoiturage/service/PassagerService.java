package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.passager.PassagerCreateRequest;
import ma.youcode.covoiturage.dto.passager.PassagerResponse;
import ma.youcode.covoiturage.dto.passager.PassagerUpdateRequest;

import java.util.List;

public interface PassagerService {

    PassagerResponse create(PassagerCreateRequest request);

    PassagerResponse getById(Long id);

    List<PassagerResponse> getAll();

    List<PassagerResponse> searchByEmail(String email);

    PassagerResponse update(Long id, Long actorId, PassagerUpdateRequest request);
}
