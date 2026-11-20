package ma.youcode.covoiturage.controller;

import lombok.RequiredArgsConstructor;

import jakarta.validation.Valid;
import ma.youcode.covoiturage.dto.passager.PassagerCreateRequest;
import ma.youcode.covoiturage.dto.passager.PassagerResponse;
import ma.youcode.covoiturage.dto.passager.PassagerUpdateRequest;
import ma.youcode.covoiturage.service.PassagerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/passagers")
@RequiredArgsConstructor
public class PassagerController {

    private final PassagerService passagerService;

    @PostMapping
    public ResponseEntity<PassagerResponse> create(
            @Valid @RequestBody PassagerCreateRequest request) {
        PassagerResponse response = passagerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PassagerResponse> getById(
            @PathVariable("id") Long id) {
        PassagerResponse response = passagerService.getById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<PassagerResponse>> getAll() {
        List<PassagerResponse> responses = passagerService.getAll();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search")
    public ResponseEntity<List<PassagerResponse>> searchByEmail(
            @RequestParam("email") String email) {
        List<PassagerResponse> responses = passagerService.searchByEmail(email);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PassagerResponse> update(
            @PathVariable("id") Long id,
            @RequestHeader("X-Passager-Id") Long actorId,
            @Valid @RequestBody PassagerUpdateRequest request) {
        PassagerResponse response = passagerService.update(id, actorId, request);
        return ResponseEntity.ok(response);
    }
}
