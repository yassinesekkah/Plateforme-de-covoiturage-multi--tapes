package ma.youcode.covoiturage.mapper;

import ma.youcode.covoiturage.dto.trajet.TrajetCreateRequest;
import ma.youcode.covoiturage.dto.trajet.TrajetResponse;
import ma.youcode.covoiturage.dto.trajet.TrajetDetailResponse;
import ma.youcode.covoiturage.entity.Trajet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {PointArretMapper.class, VehiculeMapper.class})
public interface TrajetMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "conducteur", ignore = true)
    @Mapping(target = "vehicule", ignore = true)
    @Mapping(target = "departureCity", ignore = true)
    @Mapping(target = "arrivalCity", ignore = true)
    @Mapping(target = "departureTime", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "pointsArret", ignore = true)
    Trajet toEntity(TrajetCreateRequest request);

    @Mapping(target = "conducteurId", source = "conducteur.id")
    @Mapping(target = "vehiculeId", source = "vehicule.id")
    TrajetResponse toResponse(Trajet trajet);

    TrajetDetailResponse toDetailResponse(Trajet trajet);
}
