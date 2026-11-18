package ma.youcode.covoiturage.controller;

import ma.youcode.covoiturage.dto.reservation.ReservationDetailResponse;
import ma.youcode.covoiturage.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conducteurs/{conducteurId}/trajets/{trajetId}/reservations")
public class ConducteurReservationController {
    private final ReservationService reservationService;

    public ConducteurReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public ResponseEntity<List<ReservationDetailResponse>> getByTrajet(
            @PathVariable("conducteurId") Long conducteurId,
            @PathVariable("trajetId") Long trajetId,
            @RequestHeader("X-Conducteur-Id") Long actorId) {
        return ResponseEntity.ok(
                reservationService.getByTrajetForConducteur(conducteurId, trajetId, actorId));
    }
}
