package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.passager.PassagerCreateRequest;
import ma.youcode.covoiturage.dto.passager.PassagerResponse;
import ma.youcode.covoiturage.dto.passager.PassagerUpdateRequest;
import ma.youcode.covoiturage.entity.Passager;
import ma.youcode.covoiturage.exception.ResourceNotFoundException;
import ma.youcode.covoiturage.exception.BadRequestException;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.exception.ForbiddenException;
import ma.youcode.covoiturage.mapper.PassagerMapper;
import ma.youcode.covoiturage.repository.PassagerRepository;
import ma.youcode.covoiturage.service.impl.PassagerServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PassagerServiceImplTest {

    @Mock
    private PassagerRepository passagerRepository;

    @Mock
    private PassagerMapper passagerMapper;

    @InjectMocks
    private PassagerServiceImpl passagerService;

    @Test
    void shouldCreatePassagerAndReturnGeneratedId() {
        PassagerCreateRequest request = new PassagerCreateRequest(
                "Bennani", "Salma", "salma@example.com", "0612345678"
        );
        Passager passager = new Passager();
        Passager savedPassager = new Passager();
        savedPassager.setId(1L);
        PassagerResponse expected = new PassagerResponse(
                1L, "Bennani", "Salma", "salma@example.com", "0612345678"
        );

        when(passagerRepository.existsByEmail(request.email())).thenReturn(false);
        when(passagerMapper.toEntity(request)).thenReturn(passager);
        when(passagerRepository.save(passager)).thenReturn(savedPassager);
        when(passagerMapper.toResponse(savedPassager)).thenReturn(expected);

        PassagerResponse result = passagerService.create(request);

        assertThat(result).isEqualTo(expected);
        verify(passagerRepository).save(passager);
        verify(passagerMapper).toResponse(savedPassager);
    }

    @Test
    void shouldReturnExistingPassager() {
        Passager passager = new Passager();
        passager.setId(1L);
        PassagerResponse expected = new PassagerResponse(
                1L, "Bennani", "Salma", "salma@example.com", "0612345678"
        );

        when(passagerRepository.findById(1L)).thenReturn(Optional.of(passager));
        when(passagerMapper.toResponse(passager)).thenReturn(expected);

        PassagerResponse result = passagerService.getById(1L);

        assertThat(result).isEqualTo(expected);
        verify(passagerRepository).findById(1L);
    }

    @Test
    void shouldRejectConsultationWhenPassagerDoesNotExist() {
        when(passagerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passagerService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Passager introuvable avec l'identifiant 99");

        verifyNoInteractions(passagerMapper);
    }

    @Test
    void shouldReturnAllPassagers() {
        Passager first = new Passager();
        Passager second = new Passager();
        PassagerResponse firstResponse = new PassagerResponse(
                1L, "Bennani", "Salma", "salma@example.com", "0612345678"
        );
        PassagerResponse secondResponse = new PassagerResponse(
                2L, "Alami", "Sara", "sara@example.com", "0623456789"
        );
        when(passagerRepository.findAll()).thenReturn(List.of(first, second));
        when(passagerMapper.toResponse(first)).thenReturn(firstResponse);
        when(passagerMapper.toResponse(second)).thenReturn(secondResponse);

        assertThat(passagerService.getAll()).containsExactly(firstResponse, secondResponse);
    }

    @Test
    void shouldReturnEmptyListWhenNoPassagersExist() {
        when(passagerRepository.findAll()).thenReturn(List.of());

        assertThat(passagerService.getAll()).isEmpty();
        verifyNoInteractions(passagerMapper);
    }

    @Test
    void shouldSearchByEmailAfterTrimmingSpaces() {
        Passager passager = new Passager();
        PassagerResponse response = new PassagerResponse(
                1L, "Bennani", "Salma", "salma@example.com", "0612345678"
        );
        when(passagerRepository.findByEmailContainingIgnoreCase("salma"))
                .thenReturn(List.of(passager));
        when(passagerMapper.toResponse(passager)).thenReturn(response);

        assertThat(passagerService.searchByEmail("  salma  "))
                .containsExactly(response);
        verify(passagerRepository).findByEmailContainingIgnoreCase("salma");
    }

    @Test
    void shouldReturnEmptySearchResults() {
        when(passagerRepository.findByEmailContainingIgnoreCase("introuvable"))
                .thenReturn(List.of());

        assertThat(passagerService.searchByEmail("introuvable")).isEmpty();
        verifyNoInteractions(passagerMapper);
    }

    @Test
    void shouldRejectBlankSearchEmail() {
        assertThatThrownBy(() -> passagerService.searchByEmail("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'email de recherche est obligatoire");
        verifyNoInteractions(passagerRepository, passagerMapper);
    }

    @Test
    void shouldRejectNullSearchEmail() {
        assertThatThrownBy(() -> passagerService.searchByEmail(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'email de recherche est obligatoire");
        verifyNoInteractions(passagerRepository, passagerMapper);
    }

    @Test
    void shouldUpdateOwnProfile() {
        Passager passager = new Passager();
        passager.setId(1L);
        passager.setEmail("salma@example.com");
        PassagerUpdateRequest request = updateRequest();
        PassagerResponse expected = new PassagerResponse(
                1L, "Bennani", "Salma", "salma@example.com", "0699999999"
        );
        when(passagerRepository.findById(1L)).thenReturn(Optional.of(passager));
        when(passagerRepository.existsByEmailAndIdNot(request.email(), 1L))
                .thenReturn(false);
        when(passagerRepository.save(passager)).thenReturn(passager);
        when(passagerMapper.toResponse(passager)).thenReturn(expected);

        assertThat(passagerService.update(1L, 1L, request)).isEqualTo(expected);
        verify(passagerMapper).updateEntity(request, passager);
        verify(passagerRepository).save(passager);
        verify(passagerRepository).existsByEmailAndIdNot(request.email(), 1L);
    }

    @Test
    void shouldRejectUpdateOfAnotherProfile() {
        Passager passager = new Passager();
        passager.setId(1L);
        when(passagerRepository.findById(1L)).thenReturn(Optional.of(passager));

        assertThatThrownBy(() -> passagerService.update(1L, 2L, updateRequest()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Vous ne pouvez modifier que votre propre profil");
        verify(passagerRepository, never()).save(any(Passager.class));
        verifyNoInteractions(passagerMapper);
    }

    @Test
    void shouldRejectUpdateWhenPassagerDoesNotExist() {
        when(passagerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passagerService.update(99L, 99L, updateRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Passager introuvable avec l'identifiant 99");
        verify(passagerRepository, never()).save(any(Passager.class));
        verifyNoInteractions(passagerMapper);
    }

    @Test
    void shouldRejectCreationWhenEmailAlreadyExists() {
        PassagerCreateRequest request = new PassagerCreateRequest(
                "Bennani", "Salma", "salma@example.com", "0612345678"
        );
        when(passagerRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> passagerService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cet email est deja utilise par un passager");

        verify(passagerRepository, never()).save(any(Passager.class));
        verifyNoInteractions(passagerMapper);
    }

    @Test
    void shouldRejectUpdateWhenEmailBelongsToAnotherPassager() {
        Passager passager = new Passager();
        passager.setId(1L);
        PassagerUpdateRequest request = updateRequest();
        when(passagerRepository.findById(1L)).thenReturn(Optional.of(passager));
        when(passagerRepository.existsByEmailAndIdNot(request.email(), 1L))
                .thenReturn(true);

        assertThatThrownBy(() -> passagerService.update(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cet email est deja utilise par un autre passager");

        verify(passagerRepository, never()).save(any(Passager.class));
        verifyNoInteractions(passagerMapper);
    }

    private PassagerUpdateRequest updateRequest() {
        return new PassagerUpdateRequest(
                "Bennani", "Salma", "salma@example.com", "0699999999"
        );
    }
}
