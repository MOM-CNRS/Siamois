package fr.siamois.infrastructure.database.repositories.permissions;

import fr.siamois.domain.models.permissions.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileRepository extends CrudRepository<Profile, Integer> {
    Optional<Profile> findByCode(String code);

    Optional<Profile> findByCodeAndInstitutionId(String code, Long institutionId);

    Optional<Profile> findByCodeAndInstitutionIdAndActionUnitId(String code, Long institutionId, Long actionUnitId);

    void deleteAllByActionUnitId(Long actionUnitId);

    List<Profile> findProfilesByInstitutionId(Long institutionId);

    List<Profile> findProfilesByActionUnitId(Long actionUnitId);

    @Query("SELECT p FROM Profile p WHERE p.actionUnit IS NULL AND p.institution IS NULL")
    List<Profile> findAllOfInstanceLevel();
}
