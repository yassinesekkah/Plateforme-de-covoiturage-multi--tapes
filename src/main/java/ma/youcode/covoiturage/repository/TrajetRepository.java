package ma.youcode.covoiturage.repository;

import ma.youcode.covoiturage.entity.Trajet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import ma.youcode.covoiturage.dto.trajet.TrajetSearchResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Query("""
            select new ma.youcode.covoiturage.dto.trajet.TrajetSearchResult(
                t.id, t.departureTime,
                montee.id, montee.city, montee.passageTime,
                descente.id, descente.city, descente.passageTime,
                descente.priceFromDeparture - montee.priceFromDeparture,
                t.offeredSeats - coalesce(sum(r.nombrePlaces), 0))
            from Trajet t
            join t.pointsArret montee
            join t.pointsArret descente
            left join Reservation r on r.trajet = t
                and r.statut = ma.youcode.covoiturage.enums.StatutReservation.CONFIRMEE
            where lower(montee.city) = lower(:departureCity)
                and lower(descente.city) = lower(:arrivalCity)
                and montee.stopOrder < descente.stopOrder
                and t.status = ma.youcode.covoiturage.enums.StatutTrajet.PUBLIE
                and t.departureTime >= :startDate
                and t.departureTime < :endDate
                and (:maxPrice is null
                    or descente.priceFromDeparture - montee.priceFromDeparture <= :maxPrice)
            group by t.id, t.departureTime, t.offeredSeats,
                montee.id, montee.city, montee.passageTime, montee.priceFromDeparture,
                descente.id, descente.city, descente.passageTime, descente.priceFromDeparture
            having t.offeredSeats - coalesce(sum(r.nombrePlaces), 0) >= :seats
            order by t.departureTime asc, t.id asc
            """)
    Slice<TrajetSearchResult> search(
            @Param("departureCity") String departureCity,
            @Param("arrivalCity") String arrivalCity,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("seats") int seats,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);
}
