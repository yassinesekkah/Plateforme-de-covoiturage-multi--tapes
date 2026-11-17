package ma.youcode.covoiturage.repository;

import ma.youcode.covoiturage.entity.Passager;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PassagerRepository extends JpaRepository<Passager, Long> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<Passager> findByEmailContainingIgnoreCase(String email);
}
