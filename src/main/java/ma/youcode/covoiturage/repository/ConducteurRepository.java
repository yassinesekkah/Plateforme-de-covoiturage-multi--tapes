package ma.youcode.covoiturage.repository;

import ma.youcode.covoiturage.entity.Conducteur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConducteurRepository
        extends JpaRepository<Conducteur, Long> {

    boolean existsByEmail(String email);

    boolean existsByLicenseNumber(String licenseNumber);

    List<Conducteur> findByLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String lastName,
            String email
    );

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);
}