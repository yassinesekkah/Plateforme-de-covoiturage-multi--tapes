package ma.youcode.covoiturage.service.impl;

import ma.youcode.covoiturage.dto.ConducteurCreateRequest;
import ma.youcode.covoiturage.dto.ConducteurResponse;
import ma.youcode.covoiturage.entity.Conducteur;
import ma.youcode.covoiturage.exception.BusinessException;
import ma.youcode.covoiturage.mapper.ConducteurMapper;
import ma.youcode.covoiturage.repository.ConducteurRepository;
import ma.youcode.covoiturage.service.ConducteurService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConducteurServiceImpl implements ConducteurService {

    private static final Logger log =
            LoggerFactory.getLogger(ConducteurServiceImpl.class);

    private final ConducteurRepository conducteurRepository;
    private final ConducteurMapper conducteurMapper;

    public ConducteurServiceImpl(
            ConducteurRepository conducteurRepository,
            ConducteurMapper conducteurMapper) {

        this.conducteurRepository = conducteurRepository;
        this.conducteurMapper = conducteurMapper;
    }

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

}
