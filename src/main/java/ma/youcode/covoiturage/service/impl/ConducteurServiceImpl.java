package ma.youcode.covoiturage.service.impl;

import lombok.RequiredArgsConstructor;

import ma.youcode.covoiturage.dto.conducteur.ConducteurCreateRequest;
import ma.youcode.covoiturage.dto.conducteur.ConducteurResponse;
import ma.youcode.covoiturage.dto.conducteur.ConducteurUpdateRequest;
import ma.youcode.covoiturage.entity.Conducteur;
import ma.youcode.covoiturage.exception.BadRequestException;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.exception.ForbiddenException;
import ma.youcode.covoiturage.exception.ResourceNotFoundException;
import ma.youcode.covoiturage.mapper.ConducteurMapper;
import ma.youcode.covoiturage.repository.ConducteurRepository;
import ma.youcode.covoiturage.service.ConducteurService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConducteurServiceImpl implements ConducteurService {

    private static final Logger log =
            LoggerFactory.getLogger(ConducteurServiceImpl.class);

    private final ConducteurRepository conducteurRepository;
    private final ConducteurMapper conducteurMapper;

    @Override
    @Transactional
    public ConducteurResponse create(ConducteurCreateRequest request){

        if (conducteurRepository.existsByEmail(request.email())) {
            throw new BusinessException(
                    "Cet email est deja utilise par un conducteur"
            );
        }

        if (conducteurRepository.existsByLicenseNumber(
                request.licenseNumber())) {
            throw new BusinessException(
                    "Ce numero de permis est deja utilise"
            );
        }

        Conducteur conducteur = conducteurMapper.toEntity(request);

        Conducteur savedConducteur =
                conducteurRepository.save(conducteur);

        log.info(
                "Creation du conducteur avec l'identifiant {}",
                savedConducteur.getId()
        );

        return conducteurMapper.toResponse(savedConducteur);
    }

    @Override
    @Transactional(readOnly = true)
    public ConducteurResponse getById(Long id){

        Conducteur conducteur = conducteurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conducteur introuvable avec l'identifiant " + id
                ));

        return conducteurMapper.toResponse(conducteur);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConducteurResponse> getAll() {

        return conducteurRepository.findAll()
                .stream()
                .map(conducteurMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConducteurResponse> search(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            throw new BadRequestException(
                    "Le mot-cle de recherche est obligatoire"
            );
        }

        String searchTerm = keyword.trim();

        return conducteurRepository
                .findByLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        searchTerm,
                        searchTerm
                )
                .stream()
                .map(conducteurMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ConducteurResponse update(
            Long id,
            Long actorId,
            ConducteurUpdateRequest request) {

        Conducteur conducteur = conducteurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conducteur introuvable avec l'identifiant " + id
                ));

        if (!conducteur.getId().equals(actorId)) {
            throw new ForbiddenException(
                    "Vous ne pouvez modifier que votre propre profil"
            );
        }

        if (conducteurRepository.existsByEmailAndIdNot(
                request.email(), id)) {
            throw new BusinessException(
                    "Cet email est deja utilise par un autre conducteur"
            );
        }

        if (conducteurRepository.existsByLicenseNumberAndIdNot(
                request.licenseNumber(), id)) {
            throw new BusinessException(
                    "Ce numero de permis est deja utilise par un autre conducteur"
            );
        }

        conducteurMapper.updateEntity(request, conducteur);

        Conducteur savedConducteur =
                conducteurRepository.save(conducteur);

        log.info(
                "Modification du conducteur avec l'identifiant {}",
                savedConducteur.getId()
        );

        return conducteurMapper.toResponse(savedConducteur);
    }

}
