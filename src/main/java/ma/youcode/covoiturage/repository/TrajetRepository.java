package ma.youcode.covoiturage.repository;

import ma.youcode.covoiturage.entity.Trajet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface TrajetRepository extends JpaRepository<Trajet, Long> {

    boolean existsByVehiculeId(Long vehiculeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trajet t where t.id = :id")
    Optional<Trajet> findByIdForReservation(@Param("id") Long id);

    @Query("""
            select t from Trajet t
            join fetch t.conducteur
            join fetch t.vehicule v
            join fetch v.conducteur
            left join fetch t.pointsArret
            where t.id = :id
            """)
    Optional<Trajet> findDetailById(@Param("id") Long id);
}
