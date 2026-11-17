package ma.youcode.covoiturage.repository;

import ma.youcode.covoiturage.entity.Vehicule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehiculeRepository
        extends JpaRepository<Vehicule, Long> {

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumberAndIdNot(
            String registrationNumber,
            Long id
    );

    List<Vehicule> findByConducteurId(Long conducteurId);
}