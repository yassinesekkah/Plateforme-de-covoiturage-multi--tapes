package ma.youcode.covoiturage.service.impl;

import lombok.RequiredArgsConstructor;

import ma.youcode.covoiturage.dto.vehicule.*;
import ma.youcode.covoiturage.entity.Conducteur;
import ma.youcode.covoiturage.entity.Vehicule;
import ma.youcode.covoiturage.exception.*;
import ma.youcode.covoiturage.mapper.VehiculeMapper;
import ma.youcode.covoiturage.repository.ConducteurRepository;
import ma.youcode.covoiturage.repository.VehiculeRepository;
import ma.youcode.covoiturage.repository.TrajetRepository;
import ma.youcode.covoiturage.service.VehiculeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehiculeServiceImpl implements VehiculeService {
    private static final Logger log = LoggerFactory.getLogger(VehiculeServiceImpl.class);
    private final VehiculeRepository vehiculeRepository;
    private final ConducteurRepository conducteurRepository;
    private final VehiculeMapper vehiculeMapper;
    private final TrajetRepository trajetRepository;

    @Override
    @Transactional
    public VehiculeResponse create(Long conducteurId, Long actorId, VehiculeCreateRequest request) {
        Conducteur conducteur = conducteurRepository.findById(conducteurId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conducteur introuvable avec l'identifiant " + conducteurId));
        checkOwner(conducteur.getId(), actorId);
        if (vehiculeRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw new BusinessException("Cette immatriculation est deja utilisee");
        }
        Vehicule vehicule = vehiculeMapper.toEntity(request);
        vehicule.setConducteur(conducteur);
        Vehicule saved = vehiculeRepository.save(vehicule);
        log.info("Creation du vehicule avec l'identifiant {}", saved.getId());
        return vehiculeMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VehiculeResponse getById(Long id) {
        return vehiculeMapper.toResponse(findVehicule(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehiculeResponse> getByConducteur(Long conducteurId) {
        if (!conducteurRepository.existsById(conducteurId)) {
            throw new ResourceNotFoundException(
                    "Conducteur introuvable avec l'identifiant " + conducteurId);
        }
        return vehiculeRepository.findByConducteurId(conducteurId)
                .stream().map(vehiculeMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public VehiculeResponse update(Long id, Long actorId, VehiculeUpdateRequest request) {
        Vehicule vehicule = findVehicule(id);
        checkOwner(vehicule.getConducteur().getId(), actorId);
        if (vehiculeRepository.existsByRegistrationNumberAndIdNot(request.registrationNumber(), id)) {
            throw new BusinessException("Cette immatriculation est deja utilisee par un autre vehicule");
        }
        vehiculeMapper.updateEntity(request, vehicule);
        Vehicule saved = vehiculeRepository.save(vehicule);
        log.info("Modification du vehicule avec l'identifiant {}", saved.getId());
        return vehiculeMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long actorId) {
        Vehicule vehicule = findVehicule(id);
        checkOwner(vehicule.getConducteur().getId(), actorId);
        if (trajetRepository.existsByVehiculeId(id)) {
            throw new BusinessException("Ce vehicule est utilise et ne peut pas etre supprime");
        }
        try {
            vehiculeRepository.delete(vehicule);
            // Force le DELETE dans cette methode pour intercepter un refus de cle etrangere.
            vehiculeRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23503".equals(sql.getSQLState())) {
                    throw new BusinessException("Ce vehicule est utilise et ne peut pas etre supprime");
                }
            }
            throw exception;
        }
        log.info("Suppression du vehicule avec l'identifiant {}", id);
    }

    private Vehicule findVehicule(Long id) {
        return vehiculeRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Vehicule introuvable avec l'identifiant " + id));
    }

    private void checkOwner(Long ownerId, Long actorId) {
        if (!ownerId.equals(actorId)) {
            throw new ForbiddenException("Vous ne pouvez gerer que vos propres vehicules");
        }
    }
}
