package fr.siamois.infrastructure.database.repositories.institution;


import fr.siamois.domain.models.institution.Institution;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;

import java.util.Optional;
import java.util.Set;

public interface InstitutionRepository extends CrudRepository<Institution, Long>, RevisionRepository<Institution, Long, Long> {

    Optional<Institution> findInstitutionByIdentifier(@NotNull String identifier);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT i.* FROM institution i " +
                    "JOIN action_unit au ON i.institution_id = au.fk_institution_id " +
                    "JOIN team_member tm ON tm.fk_action_unit_id = au.action_unit_id " +
                    "WHERE tm.fk_person_id = :personId"
    )
    Set<Institution> findAllAsMember(Long personId);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT i.* FROM institution i " +
                    "JOIN institution_manager im ON im.fk_institution_id = i.institution_id " +
                    "WHERE im.fk_person_id = :personId"
    )
    Set<Institution> findAllAsInstitutionManager(Long personId);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT i.* FROM institution i " +
                    "JOIN action_manager am ON i.institution_id = am.fk_institution_id " +
                    "WHERE am.fk_person_id = :personId"
    )
    Set<Institution> findAllAsActionManager(Long personId);

    @Query(
            nativeQuery = true,
            value = "SELECT COUNT(*) >= 1 " +
                    "FROM institution_manager im " +
                    "WHERE im.fk_person_id = :personId AND im.fk_institution_id = :institutionId"
    )
    boolean personIsInstitutionManagerOf(Long institutionId, Long personId);



}
