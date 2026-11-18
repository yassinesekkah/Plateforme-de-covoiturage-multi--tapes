package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.dto.reservation.ReservationResponse;
import ma.youcode.covoiturage.dto.reservation.ReservationDetailResponse;
import java.util.List;

public interface ReservationService {
    ReservationResponse create(Long passagerId, Long actorId, ReservationCreateRequest request);

    int getRemainingSeats(Long trajetId);

    List<ReservationDetailResponse> getByPassager(Long passagerId, Long actorId);

    List<ReservationDetailResponse> getByTrajetForConducteur(Long conducteurId, Long trajetId, Long actorId);
}
