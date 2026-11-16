package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.ConducteurCreateRequest;
import ma.youcode.covoiturage.dto.ConducteurResponse;
import ma.youcode.covoiturage.entity.Conducteur;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.mapper.ConducteurMapper;
import ma.youcode.covoiturage.repository.ConducteurRepository;
import ma.youcode.covoiturage.service.impl.ConducteurServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}