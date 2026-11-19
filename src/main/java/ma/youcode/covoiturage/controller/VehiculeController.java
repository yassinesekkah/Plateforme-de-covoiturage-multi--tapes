package ma.youcode.covoiturage.controller;

import jakarta.validation.Valid;
import ma.youcode.covoiturage.dto.vehicule.*;
import ma.youcode.covoiturage.service.VehiculeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class VehiculeController {
    private final VehiculeService vehiculeService;

    public VehiculeController(VehiculeService vehiculeService) {
        this.vehiculeService = vehiculeService;
    }

    @PostMapping("/conducteurs/{conducteurId}/vehicules")
    public ResponseEntity<VehiculeResponse> create(
            @PathVariable("conducteurId") Long conducteurId,
            @RequestHeader("X-Conducteur-Id") Long actorId,
            @Valid @RequestBody VehiculeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehiculeService.create(conducteurId, actorId, request));
    }

    @GetMapping("/vehicules/{id}")
    public ResponseEntity<VehiculeResponse> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(vehiculeService.getById(id));
    }

    @GetMapping("/conducteurs/{conducteurId}/vehicules")
    public ResponseEntity<List<VehiculeResponse>> getByConducteur(
            @PathVariable("conducteurId") Long conducteurId) {
        return ResponseEntity.ok(vehiculeService.getByConducteur(conducteurId));
    }

    @PutMapping("/vehicules/{id}")
    public ResponseEntity<VehiculeResponse> update(
            @PathVariable("id") Long id,
            @RequestHeader("X-Conducteur-Id") Long actorId,
            @Valid @RequestBody VehiculeUpdateRequest request) {
        return ResponseEntity.ok(vehiculeService.update(id, actorId, request));
    }

    @DeleteMapping("/vehicules/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id,
            @RequestHeader("X-Conducteur-Id") Long actorId) {
        vehiculeService.delete(id, actorId);
        return ResponseEntity.noContent().build();
    }
}
