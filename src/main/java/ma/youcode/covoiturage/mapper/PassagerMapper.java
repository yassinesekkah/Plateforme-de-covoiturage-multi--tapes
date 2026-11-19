package ma.youcode.covoiturage.mapper;

import ma.youcode.covoiturage.dto.passager.PassagerCreateRequest;
import ma.youcode.covoiturage.dto.passager.PassagerResponse;
import ma.youcode.covoiturage.dto.passager.PassagerUpdateRequest;
import ma.youcode.covoiturage.entity.Passager;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PassagerMapper {

    @Mapping(target = "id", ignore = true)
    Passager toEntity(PassagerCreateRequest request);

    PassagerResponse toResponse(Passager passager);

    @Mapping(target = "id", ignore = true)
    void updateEntity(
            PassagerUpdateRequest request,
            @MappingTarget Passager passager
    );
}
