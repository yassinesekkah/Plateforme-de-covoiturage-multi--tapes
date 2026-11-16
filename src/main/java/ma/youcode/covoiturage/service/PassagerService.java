package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.PassagerCreateRequest;
import ma.youcode.covoiturage.dto.PassagerResponse;
import ma.youcode.covoiturage.dto.PassagerUpdateRequest;

import java.util.List;

public interface PassagerService {

    PassagerResponse create(PassagerCreateRequest request);

    PassagerResponse getById(Long id);

    List<PassagerResponse> getAll();

    List<PassagerResponse> searchByEmail(String email);

    PassagerResponse update(Long id, Long actorId, PassagerUpdateRequest request);
}
