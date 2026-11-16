package ma.youcode.covoiturage.controller;

import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.exception.ForbiddenException;
import ma.youcode.covoiturage.exception.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ma.youcode.covoiturage.exception.BadRequestException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/test-errors")
@Profile("test-errors")
public class ErrorTestController {

    @GetMapping("/not-found")
    public void testNotFound() {
        throw new ResourceNotFoundException(
                "Conducteur de test introuvable"
        );
    }

    @GetMapping("/forbidden")
    public void testForbidden() {
        throw new ForbiddenException(
                "Ce vehicule appartient a un autre conducteur"
        );
    }

    @GetMapping("/conflict")
    public void testConflict() {
        throw new BusinessException(
                "Le trajet est complet"
        );
    }

    @GetMapping("/bad-request")
    public void testBadRequest() {
        throw new BadRequestException(
                "Le point de montee doit preceder le point de descente"
        );
    }

    public record TestRequest(
            @NotBlank(message = "Le nom est obligatoire")
            String nom
    ) {
    }

    @PostMapping("/validation")
    public void testValidation(@Valid @RequestBody TestRequest request) {
    }

    @GetMapping("/number/{id}")
    public void testNumber(@PathVariable("id") Long id) {
    }

    @GetMapping("/internal")
    public void testInternalError() {
        throw new IllegalStateException(
                "Erreur technique simulee pour le test"
        );
    }
}