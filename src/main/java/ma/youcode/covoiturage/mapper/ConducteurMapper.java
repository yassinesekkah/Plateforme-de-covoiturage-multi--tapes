package ma.youcode.covoiturage.mapper;

import ma.youcode.covoiturage.dto.conducteur.ConducteurCreateRequest;
import ma.youcode.covoiturage.dto.conducteur.ConducteurResponse;
import ma.youcode.covoiturage.dto.conducteur.ConducteurUpdateRequest;
import ma.youcode.covoiturage.entity.Conducteur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ConducteurMapper {

    @Mapping(target = "id", ignore = true)
    Conducteur toEntity(ConducteurCreateRequest request);

    ConducteurResponse toResponse(Conducteur conducteur);

    @Mapping(target = "id", ignore = true)
    void updateEntity(
            ConducteurUpdateRequest request,
            @MappingTarget Conducteur conducteur
    );
}