package ma.youcode.covoiturage.controller;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import ma.youcode.covoiturage.dto.conducteur.ConducteurCreateRequest;
import ma.youcode.covoiturage.dto.conducteur.ConducteurResponse;
import ma.youcode.covoiturage.dto.conducteur.ConducteurUpdateRequest;
import ma.youcode.covoiturage.service.ConducteurService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conducteurs")
@RequiredArgsConstructor
public class ConducteurController {

    private final ConducteurService conducteurService;

    @PostMapping
    public ResponseEntity<ConducteurResponse> create(
            @Valid @RequestBody ConducteurCreateRequest request) {

        ConducteurResponse response = conducteurService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConducteurResponse> getById(
            @PathVariable("id") Long id) {

        ConducteurResponse response = conducteurService.getById(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ConducteurResponse>> getAll() {

        List<ConducteurResponse> responses = conducteurService.getAll();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ConducteurResponse>> search(
            @RequestParam("keyword") String keyword) {

        List<ConducteurResponse> responses =
                conducteurService.search(keyword);

        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConducteurResponse> update(
            @PathVariable("id") Long id,
            @RequestHeader("X-Conducteur-Id") Long actorId,
            @Valid @RequestBody ConducteurUpdateRequest request) {

        ConducteurResponse response =
                conducteurService.update(id, actorId, request);

        return ResponseEntity.ok(response);
    }
}