package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.ConducteurCreateRequest;
import ma.youcode.covoiturage.dto.ConducteurResponse;
import ma.youcode.covoiturage.dto.ConducteurUpdateRequest;
import ma.youcode.covoiturage.entity.Conducteur;
import ma.youcode.covoiturage.exception.BadRequestException;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.exception.ForbiddenException;
import ma.youcode.covoiturage.exception.ResourceNotFoundException;
import ma.youcode.covoiturage.mapper.ConducteurMapper;
import ma.youcode.covoiturage.repository.ConducteurRepository;
import ma.youcode.covoiturage.service.impl.ConducteurServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConducteurServiceImplTest {

    @Mock
    private ConducteurRepository conducteurRepository;

    @Mock
    private ConducteurMapper conducteurMapper;

    @InjectMocks
    private ConducteurServiceImpl conducteurService;

    private ConducteurCreateRequest request;

    @BeforeEach
    void setUp() {
        request = new ConducteurCreateRequest(
                "Alami",
                "Hamid",
                "hamid@example.com",
                "0612345678",
                "AB123456"
        );
    }

    private ConducteurUpdateRequest updateRequest() {
        return new ConducteurUpdateRequest(
                "Alami",
                "Hamid",
                "hamid@example.com",
                "0699999999",
                "AB123456"
        );
    }

    private Conducteur existingConducteur() {
        Conducteur conducteur = new Conducteur();
        conducteur.setId(1L);
        conducteur.setEmail("hamid@example.com");
        conducteur.setLicenseNumber("AB123456");
        return conducteur;
    }

    @Test
    void shouldCreateConducteurWhenDataIsValid() {
        // Arrange : preparer les reponses des mocks
        Conducteur conducteur = new Conducteur();

        Conducteur savedConducteur = new Conducteur();
        savedConducteur.setId(1L);

        ConducteurResponse expectedResponse = new ConducteurResponse(
                1L,
                "Alami",
                "Hamid",
                "hamid@example.com",
                "0612345678",
                "AB123456"
        );

        when(conducteurRepository.existsByEmail(request.email()))
                .thenReturn(false);

        when(conducteurRepository.existsByLicenseNumber(
                request.licenseNumber()))
                .thenReturn(false);

        when(conducteurMapper.toEntity(request))
                .thenReturn(conducteur);

        when(conducteurRepository.save(conducteur))
                .thenReturn(savedConducteur);

        when(conducteurMapper.toResponse(savedConducteur))
                .thenReturn(expectedResponse);

        // Act : appeler le service
        ConducteurResponse result = conducteurService.create(request);

        // Assert : verifier le resultat et la sauvegarde
        assertThat(result).isEqualTo(expectedResponse);

        verify(conducteurRepository).save(conducteur);
    }

    @Test
    void shouldRejectCreationWhenEmailAlreadyExists() {
        // Arrange
        when(conducteurRepository.existsByEmail(request.email()))
                .thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> conducteurService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cet email est deja utilise par un conducteur");

        verify(conducteurRepository, never())
                .save(any(Conducteur.class));

        verifyNoInteractions(conducteurMapper);
    }

    @Test
    void shouldRejectCreationWhenLicenseNumberAlreadyExists() {
        // Arrange
        when(conducteurRepository.existsByEmail(request.email()))
                .thenReturn(false);

        when(conducteurRepository.existsByLicenseNumber(
                request.licenseNumber()))
                .thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> conducteurService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ce numero de permis est deja utilise");

        verify(conducteurRepository, never())
                .save(any(Conducteur.class));

        verifyNoInteractions(conducteurMapper);
    }

    @Test
    void shouldReturnAllConducteurs() {
        // Arrange
        Conducteur firstConducteur = new Conducteur();
        firstConducteur.setId(1L);

        Conducteur secondConducteur = new Conducteur();
        secondConducteur.setId(2L);

        ConducteurResponse firstResponse = new ConducteurResponse(
                1L, "Alami", "Hamid", "hamid@example.com",
                "0612345678", "AB123456"
        );

        ConducteurResponse secondResponse = new ConducteurResponse(
                2L, "Bennani", "Karim", "karim@example.com",
                "0623456789", "CD789012"
        );

        when(conducteurRepository.findAll())
                .thenReturn(List.of(firstConducteur, secondConducteur));

        when(conducteurMapper.toResponse(firstConducteur))
                .thenReturn(firstResponse);

        when(conducteurMapper.toResponse(secondConducteur))
                .thenReturn(secondResponse);

        // Act
        List<ConducteurResponse> result = conducteurService.getAll();

        // Assert
        assertThat(result)
                .containsExactly(firstResponse, secondResponse);

        verify(conducteurRepository).findAll();
    }

    @Test
    void shouldReturnEmptyListWhenNoConducteursExist() {
        // Arrange
        when(conducteurRepository.findAll())
                .thenReturn(List.of());

        // Act
        List<ConducteurResponse> result = conducteurService.getAll();

        // Assert
        assertThat(result).isEmpty();

        verifyNoInteractions(conducteurMapper);
    }

    @Test
    void shouldReturnMatchingConducteurs() {
        // Arrange : verifier aussi la suppression des espaces
        Conducteur conducteur = new Conducteur();
        conducteur.setId(1L);

        ConducteurResponse response = new ConducteurResponse(
                1L, "Alami", "Hamid", "hamid@example.com",
                "0612345678", "AB123456"
        );

        when(conducteurRepository
                .findByLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "Alami", "Alami"
                ))
                .thenReturn(List.of(conducteur));

        when(conducteurMapper.toResponse(conducteur))
                .thenReturn(response);

        // Act
        List<ConducteurResponse> result =
                conducteurService.search("  Alami  ");

        // Assert
        assertThat(result).containsExactly(response);

        verify(conducteurRepository)
                .findByLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "Alami", "Alami"
                );
    }

    @Test
    void shouldReturnEmptyListWhenSearchHasNoMatches() {
        // Arrange
        when(conducteurRepository
                .findByLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "introuvable", "introuvable"
                ))
                .thenReturn(List.of());

        // Act
        List<ConducteurResponse> result =
                conducteurService.search("introuvable");

        // Assert
        assertThat(result).isEmpty();

        verifyNoInteractions(conducteurMapper);
    }

    @Test
    void shouldRejectBlankSearchKeyword() {
        // Act + Assert
        assertThatThrownBy(() -> conducteurService.search("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le mot-cle de recherche est obligatoire");

        verifyNoInteractions(conducteurRepository, conducteurMapper);
    }

    @Test
    void shouldUpdateOwnProfile() {
        ConducteurUpdateRequest update = updateRequest();
        Conducteur conducteur = existingConducteur();

        ConducteurResponse response = new ConducteurResponse(
                1L, "Alami", "Hamid", "hamid@example.com",
                "0699999999", "AB123456"
        );

        when(conducteurRepository.findById(1L))
                .thenReturn(Optional.of(conducteur));
        when(conducteurRepository.existsByEmailAndIdNot(update.email(), 1L))
                .thenReturn(false);
        when(conducteurRepository.existsByLicenseNumberAndIdNot(
                update.licenseNumber(), 1L))
                .thenReturn(false);
        when(conducteurRepository.save(conducteur))
                .thenReturn(conducteur);
        when(conducteurMapper.toResponse(conducteur))
                .thenReturn(response);

        ConducteurResponse result =
                conducteurService.update(1L, 1L, update);

        assertThat(result).isEqualTo(response);
        verify(conducteurMapper).updateEntity(update, conducteur);
        verify(conducteurRepository).save(conducteur);
    }

    @Test
    void shouldRejectUpdateOfAnotherProfile() {
        when(conducteurRepository.findById(1L))
                .thenReturn(Optional.of(existingConducteur()));

        assertThatThrownBy(() ->
                conducteurService.update(1L, 2L, updateRequest()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Vous ne pouvez modifier que votre propre profil");

        verify(conducteurRepository, never()).save(any(Conducteur.class));
        verifyNoInteractions(conducteurMapper);
    }

    @Test
    void shouldRejectUpdateWhenConducteurDoesNotExist() {
        when(conducteurRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                conducteurService.update(99L, 99L, updateRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conducteur introuvable avec l'identifiant 99");

        verify(conducteurRepository, never()).save(any(Conducteur.class));
        verifyNoInteractions(conducteurMapper);
    }

    @Test
    void shouldRejectUpdateWhenEmailBelongsToAnotherConducteur() {
        ConducteurUpdateRequest update = updateRequest();

        when(conducteurRepository.findById(1L))
                .thenReturn(Optional.of(existingConducteur()));
        when(conducteurRepository.existsByEmailAndIdNot(update.email(), 1L))
                .thenReturn(true);

        assertThatThrownBy(() ->
                conducteurService.update(1L, 1L, update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cet email est deja utilise par un autre conducteur");

        verify(conducteurRepository, never()).save(any(Conducteur.class));
        verifyNoInteractions(conducteurMapper);
    }

    @Test
    void shouldRejectUpdateWhenLicenseBelongsToAnotherConducteur() {
        ConducteurUpdateRequest update = updateRequest();

        when(conducteurRepository.findById(1L))
                .thenReturn(Optional.of(existingConducteur()));
        when(conducteurRepository.existsByEmailAndIdNot(update.email(), 1L))
                .thenReturn(false);
        when(conducteurRepository.existsByLicenseNumberAndIdNot(
                update.licenseNumber(), 1L))
                .thenReturn(true);

        assertThatThrownBy(() ->
                conducteurService.update(1L, 1L, update))
                .isInstanceOf(BusinessException.class)
                .hasMessage(
                        "Ce numero de permis est deja utilise par un autre conducteur"
                );

        verify(conducteurRepository, never()).save(any(Conducteur.class));
        verifyNoInteractions(conducteurMapper);
    }
}