package ma.youcode.covoiturage.controller;

import jakarta.validation.Valid;
import ma.youcode.covoiturage.dto.trajet.TrajetCreateRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import ma.youcode.covoiturage.service.TrajetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TrajetController {

    private final TrajetService trajetService;

    public TrajetController(TrajetService trajetService) {
        this.trajetService = trajetService;
    }

    @PostMapping("/conducteurs/{conducteurId}/trajets")
    public ResponseEntity<TrajetResponse> create(
            @PathVariable("conducteurId") Long conducteurId,
            @RequestHeader("X-Conducteur-Id") Long actorId,
            @Valid @RequestBody TrajetCreateRequest request) {

        TrajetResponse response = trajetService.create(conducteurId, actorId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/trajets/{id}")
    public ResponseEntity<TrajetDetailResponse> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(trajetService.getById(id));
    }
}
