package ma.youcode.covoiturage.service;

import ma.youcode.covoiturage.dto.vehicule.*;
import ma.youcode.covoiturage.entity.*;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.mapper.VehiculeMapper;
import ma.youcode.covoiturage.repository.*;
import ma.youcode.covoiturage.service.impl.VehiculeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehiculeServiceImplTest {
    @Mock private VehiculeRepository vehiculeRepository;
    @Mock private ConducteurRepository conducteurRepository;
    @Mock private TrajetRepository trajetRepository;
    @Spy private VehiculeMapper vehiculeMapper = Mappers.getMapper(VehiculeMapper.class);
    @InjectMocks private VehiculeServiceImpl service;
    private Conducteur owner;
    private Vehicule vehicule;
    private final VehiculeCreateRequest creation = new VehiculeCreateRequest(
            "Dacia", "Logan", "12345-A-6", "Blanc", 4);
    private final VehiculeUpdateRequest update = new VehiculeUpdateRequest(
            "Dacia", "Logan", "12345-A-6", "Noir", 3);

    @BeforeEach
    void setUp() {
        owner = new Conducteur();
        owner.setId(1L);
        vehicule = vehiculeMapper.toEntity(creation);
        vehicule.setId(10L);
        vehicule.setConducteur(owner);
        clearInvocations(vehiculeMapper);
    }

    @Test
    void createsWithExistingOwnerAndGeneratedId() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(vehiculeRepository.save(any(Vehicule.class))).thenAnswer(invocation -> {
            Vehicule saved = invocation.getArgument(0);
            assertThat(saved.getId()).isNull();
            assertThat(saved.getConducteur()).isSameAs(owner);
            saved.setId(10L);
            return saved;
        });
        assertThat(service.create(1L, 1L, creation)).isEqualTo(
                new VehiculeResponse(10L, "Dacia", "Logan", "12345-A-6", "Blanc", 4, 1L));
    }

    @Test
    void rejectsCreationForMissingOwner() {
        when(conducteurRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(99L, 99L, creation))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(vehiculeRepository);
    }

    @Test
    void rejectsCreationForAnotherOwner() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.create(1L, 2L, creation))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(vehiculeRepository);
    }

    @Test
    void rejectsDuplicateRegistrationOnCreation() {
        when(conducteurRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(vehiculeRepository.existsByRegistrationNumber(creation.registrationNumber())).thenReturn(true);
        assertThatThrownBy(() -> service.create(1L, 1L, creation))
                .isInstanceOf(BusinessException.class);
        verify(vehiculeRepository, never()).save(any());
    }

    @Test
    void returnsVehicleWithOwnerId() {
        when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        assertThat(service.getById(10L).conducteurId()).isEqualTo(1L);
    }

    @Test
    void rejectsMissingVehicle() {
        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsOnlyRequestedOwnersVehicles() {
        when(conducteurRepository.existsById(1L)).thenReturn(true);
        when(vehiculeRepository.findByConducteurId(1L)).thenReturn(List.of(vehicule));
        assertThat(service.getByConducteur(1L)).hasSize(1)
                .allSatisfy(response -> assertThat(response.conducteurId()).isEqualTo(1L));
    }

    @Test
    void returnsEmptyListForOwnerWithoutVehicles() {
        when(conducteurRepository.existsById(1L)).thenReturn(true);
        assertThat(service.getByConducteur(1L)).isEmpty();
    }

    @Test
    void rejectsListForMissingOwner() {
        assertThatThrownBy(() -> service.getByConducteur(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(vehiculeRepository);
    }

    @Test
    void updatesFieldsWhileKeepingIdOwnerAndRegistration() {
        when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        when(vehiculeRepository.save(vehicule)).thenReturn(vehicule);
        VehiculeResponse result = service.update(10L, 1L, update);
        assertThat(result).isEqualTo(new VehiculeResponse(
                10L, "Dacia", "Logan", "12345-A-6", "Noir", 3, 1L));
        assertThat(vehicule.getConducteur()).isSameAs(owner);
        verify(vehiculeRepository).existsByRegistrationNumberAndIdNot("12345-A-6", 10L);
    }

    @Test
    void rejectsUpdateForAnotherOwner() {
        when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        assertThatThrownBy(() -> service.update(10L, 2L, update)).isInstanceOf(ForbiddenException.class);
        assertThat(vehicule.getColor()).isEqualTo("Blanc");
        verify(vehiculeRepository, never()).save(any());
    }

    @Test
    void rejectsUpdateWithAnotherVehiclesRegistration() {
        when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        when(vehiculeRepository.existsByRegistrationNumberAndIdNot(update.registrationNumber(), 10L))
                .thenReturn(true);
        assertThatThrownBy(() -> service.update(10L, 1L, update)).isInstanceOf(BusinessException.class);
        assertThat(vehicule.getColor()).isEqualTo("Blanc");
        verify(vehiculeRepository, never()).save(any());
    }

    @Test
    void deletesUnreferencedVehicle() {
        when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        service.delete(10L, 1L);
        verify(vehiculeRepository).delete(vehicule);
        verify(vehiculeRepository).flush();
    }

    @Test
    void rejectsDeleteForAnotherOwner() {
        when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        assertThatThrownBy(() -> service.delete(10L, 2L)).isInstanceOf(ForbiddenException.class);
        verify(vehiculeRepository, never()).delete(any());
    }

    @Test
    void translatesReferencedVehicleDeletionIntoBusinessConflict() {
        when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        doThrow(new DataIntegrityViolationException("FK", new SQLException("Referenced", "23503")))
                .when(vehiculeRepository).flush();
        assertThatThrownBy(() -> service.delete(10L, 1L)).isInstanceOf(BusinessException.class)
                .hasMessage("Ce vehicule est utilise et ne peut pas etre supprime");
    }

    @Test
    void rejectsVehicleAlreadyAssociatedWithTrajetBeforeDelete() {
        when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        when(trajetRepository.existsByVehiculeId(10L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(10L, 1L)).isInstanceOf(BusinessException.class)
                .hasMessage("Ce vehicule est utilise et ne peut pas etre supprime");
        verify(vehiculeRepository, never()).delete(any());
        verify(vehiculeRepository, never()).flush();
    }

    @Test
    void doesNotHideOtherDatabaseErrorsOnDelete() {
        when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "Other", new SQLException("Other", "23502"));
        doThrow(failure).when(vehiculeRepository).flush();
        assertThatThrownBy(() -> service.delete(10L, 1L)).isSameAs(failure);
    }
}
