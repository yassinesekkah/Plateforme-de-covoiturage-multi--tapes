package ma.youcode.covoiturage.mapper;

import ma.youcode.covoiturage.dto.trajet.PointArretCreateRequest;
import ma.youcode.covoiturage.dto.trajet.PointArretResponse;
import ma.youcode.covoiturage.entity.PointArret;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PointArretMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "trajet", ignore = true)
    @Mapping(target = "stopOrder", ignore = true)
    PointArret toEntity(PointArretCreateRequest request);

    PointArretResponse toResponse(PointArret pointArret);
}
