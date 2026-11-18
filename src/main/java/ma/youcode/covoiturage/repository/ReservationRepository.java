package ma.youcode.covoiturage.repository;

import ma.youcode.covoiturage.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ma.youcode.covoiturage.enums.StatutReservation;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Query("""
            select r from Reservation r
            join fetch r.passager
            join fetch r.trajet
            join fetch r.montee
            join fetch r.descente
            where r.passager.id = :passagerId
            order by r.dateReservation desc, r.id desc
            """)
    List<Reservation> findHistoryByPassagerId(@Param("passagerId") Long passagerId);

    @Query("""
            select r from Reservation r
            join fetch r.passager
            join fetch r.trajet
            join fetch r.montee
            join fetch r.descente
            where r.trajet.id = :trajetId and r.trajet.conducteur.id = :conducteurId
            order by r.dateReservation desc, r.id desc
            """)
    List<Reservation> findForConducteurByTrajetId(@Param("trajetId") Long trajetId,
                                                @Param("conducteurId") Long conducteurId);

    boolean existsByPassagerIdAndTrajetIdAndStatut(Long passagerId, Long trajetId,
                                                  StatutReservation statut);

    @Query("""
            select coalesce(sum(r.nombrePlaces), 0) from Reservation r
            where r.trajet.id = :trajetId and r.statut = :statut
            """)
    long sumPlacesByTrajetIdAndStatut(@Param("trajetId") Long trajetId,
                                     @Param("statut") StatutReservation statut);
}
