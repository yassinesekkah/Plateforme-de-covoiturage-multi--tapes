package ma.youcode.covoiturage.controller;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import ma.youcode.covoiturage.dto.trajet.TrajetCreateRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetDetailResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchResponse;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import ma.youcode.covoiturage.service.TrajetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TrajetController {

    private final TrajetService trajetService;

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

    @GetMapping("/trajets/search")
    public ResponseEntity<TrajetSearchResponse> search(
            @Valid @ModelAttribute TrajetSearchRequest request) {
        var results = trajetService.search(request);
        return ResponseEntity.ok(new TrajetSearchResponse(
                results.getContent(), results.getNumber(), results.getSize(), results.hasNext()));
    }

    @PatchMapping("/conducteurs/{conducteurId}/trajets/{trajetId}/annuler")
    public ResponseEntity<Void> cancel(
            @PathVariable("conducteurId") Long conducteurId,
            @PathVariable("trajetId") Long trajetId,
            @RequestHeader("X-Conducteur-Id") Long actorId) {
        trajetService.cancel(conducteurId, trajetId, actorId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/conducteurs/{conducteurId}/trajets/{trajetId}/terminer")
    public ResponseEntity<Void> finish(
            @PathVariable("conducteurId") Long conducteurId,
            @PathVariable("trajetId") Long trajetId,
            @RequestHeader("X-Conducteur-Id") Long actorId) {
        trajetService.finish(conducteurId, trajetId, actorId);
        return ResponseEntity.noContent().build();
    }
}
