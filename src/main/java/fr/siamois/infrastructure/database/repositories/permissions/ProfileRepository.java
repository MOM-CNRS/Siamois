package fr.siamois.infrastructure.database.repositories.permissions;

import fr.siamois.domain.models.permissions.Profile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfileRepository extends CrudRepository<Profile, Integer> {
    Optional<Profile> findByCode(String code);
}
