package ma.youcode.covoiturage.mapper;

import ma.youcode.covoiturage.dto.reservation.ReservationCreateRequest;
import ma.youcode.covoiturage.dto.reservation.ReservationResponse;
import ma.youcode.covoiturage.dto.reservation.ReservationDetailResponse;
import ma.youcode.covoiturage.entity.Reservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = PointArretMapper.class)
public interface ReservationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passager", ignore = true)
    @Mapping(target = "trajet", ignore = true)
    @Mapping(target = "montee", ignore = true)
    @Mapping(target = "descente", ignore = true)
    @Mapping(target = "montantTotal", ignore = true)
    @Mapping(target = "dateReservation", ignore = true)
    @Mapping(target = "statut", ignore = true)
    Reservation toEntity(ReservationCreateRequest request);

    @Mapping(target = "passagerId", source = "passager.id")
    @Mapping(target = "trajetId", source = "trajet.id")
    @Mapping(target = "monteeId", source = "montee.id")
    @Mapping(target = "descenteId", source = "descente.id")
    ReservationResponse toResponse(Reservation reservation);

    @Mapping(target = "passagerId", source = "passager.id")
    ReservationDetailResponse toDetailResponse(Reservation reservation);
}
