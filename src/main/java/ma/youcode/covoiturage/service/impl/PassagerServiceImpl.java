package ma.youcode.covoiturage.service.impl;

import ma.youcode.covoiturage.dto.PassagerCreateRequest;
import ma.youcode.covoiturage.dto.PassagerResponse;
import ma.youcode.covoiturage.dto.PassagerUpdateRequest;
import ma.youcode.covoiturage.entity.Passager;
import ma.youcode.covoiturage.exception.BadRequestException;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.exception.ForbiddenException;
import ma.youcode.covoiturage.exception.ResourceNotFoundException;
import ma.youcode.covoiturage.mapper.PassagerMapper;
import ma.youcode.covoiturage.repository.PassagerRepository;
import ma.youcode.covoiturage.service.PassagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PassagerServiceImpl implements PassagerService {

    private static final Logger log =
            LoggerFactory.getLogger(PassagerServiceImpl.class);

    private final PassagerRepository passagerRepository;
    private final PassagerMapper passagerMapper;

    public PassagerServiceImpl(
            PassagerRepository passagerRepository,
            PassagerMapper passagerMapper) {
        this.passagerRepository = passagerRepository;
        this.passagerMapper = passagerMapper;
    }

    @Override
    @Transactional
    public PassagerResponse create(PassagerCreateRequest request) {
        if (passagerRepository.existsByEmail(request.email())) {
            throw new BusinessException(
                    "Cet email est deja utilise par un passager"
            );
        }

        Passager passager = passagerMapper.toEntity(request);
        Passager savedPassager = passagerRepository.save(passager);

        log.info("Creation du passager avec l'identifiant {}", savedPassager.getId());

        return passagerMapper.toResponse(savedPassager);
    }

    @Override
    @Transactional(readOnly = true)
    public PassagerResponse getById(Long id) {
        Passager passager = passagerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Passager introuvable avec l'identifiant " + id
                ));

        return passagerMapper.toResponse(passager);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassagerResponse> getAll() {
        return passagerRepository.findAll()
                .stream()
                .map(passagerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassagerResponse> searchByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException(
                    "L'email de recherche est obligatoire"
            );
        }

        return passagerRepository.findByEmailContainingIgnoreCase(email.trim())
                .stream()
                .map(passagerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PassagerResponse update(Long id, Long actorId, PassagerUpdateRequest request) {
        Passager passager = passagerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Passager introuvable avec l'identifiant " + id
                ));

        if (!passager.getId().equals(actorId)) {
            throw new ForbiddenException(
                    "Vous ne pouvez modifier que votre propre profil"
            );
        }

        if (passagerRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new BusinessException(
                    "Cet email est deja utilise par un autre passager"
            );
        }

        passagerMapper.updateEntity(request, passager);
        Passager savedPassager = passagerRepository.save(passager);

        log.info("Modification du passager avec l'identifiant {}", savedPassager.getId());

        return passagerMapper.toResponse(savedPassager);
    }
}
