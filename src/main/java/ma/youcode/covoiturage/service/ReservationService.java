package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.dto.reservation.ReservationResponse;

public interface ReservationService {
    ReservationResponse create(Long passagerId, Long actorId, ReservationCreateRequest request);

    int getRemainingSeats(Long trajetId);
}
