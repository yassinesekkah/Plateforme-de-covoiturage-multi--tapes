package ma.youcode.covoiturage.mapper;

import ma.youcode.covoiturage.dto.ConducteurCreateRequest;
import ma.youcode.covoiturage.dto.ConducteurResponse;
import ma.youcode.covoiturage.entity.Conducteur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConducteurMapper {

    @Mapping(target = "id", ignore = true)
    Conducteur toEntity(ConducteurCreateRequest request);

    ConducteurResponse toResponse(Conducteur conducteur);
}