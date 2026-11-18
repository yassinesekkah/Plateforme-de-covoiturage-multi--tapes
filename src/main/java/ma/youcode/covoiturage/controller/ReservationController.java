package ma.youcode.covoiturage.controller;

import jakarta.validation.Valid;
import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.dto.reservation.ReservationResponse;
import ma.youcode.covoiturage.dto.reservation.ReservationDetailResponse;
import java.util.List;
import ma.youcode.covoiturage.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/passagers/{passagerId}/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @PathVariable("passagerId") Long passagerId,
            @RequestHeader("X-Passager-Id") Long actorId,
            @Valid @RequestBody ReservationCreateRequest request) {
        ReservationResponse response = reservationService.create(passagerId, actorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ReservationDetailResponse>> getByPassager(
            @PathVariable("passagerId") Long passagerId,
            @RequestHeader("X-Passager-Id") Long actorId) {
        return ResponseEntity.ok(reservationService.getByPassager(passagerId, actorId));
    }
}
