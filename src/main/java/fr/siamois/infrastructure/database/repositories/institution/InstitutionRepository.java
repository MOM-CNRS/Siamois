package fr.siamois.infrastructure.database.repositories.institution;


import fr.siamois.domain.models.institution.Institution;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;

import java.util.Collection;
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

    /**
     * Institutions the person is allowed to display, based on the profile permission system:
     * an INSTANCE-scoped profile holding one of {@code instancePermissionCodes} grants every institution,
     * an ORGANISATION-scoped profile holding {@code organizationAccessCode} grants its institution,
     * and a PROJECT-scoped profile grants the institution owning its action unit.
     */
    @Query("""
            SELECT DISTINCT i FROM Institution i
            WHERE EXISTS (
                SELECT a FROM PersonProfileAssignment a
                JOIN a.profile prof
                JOIN prof.permissions perm
                WHERE a.person.id = :personId
                  AND ((prof.scope = fr.siamois.domain.models.permissions.PermissionScopeType.INSTANCE
                            AND perm.code IN :instancePermissionCodes)
                    OR (prof.scope = fr.siamois.domain.models.permissions.PermissionScopeType.ORGANISATION
                            AND prof.institution = i
                            AND perm.code = :organizationAccessCode))
            )
            OR EXISTS (
                SELECT a2 FROM PersonProfileAssignment a2
                JOIN a2.profile prof2
                WHERE a2.person.id = :personId
                  AND prof2.scope = fr.siamois.domain.models.permissions.PermissionScopeType.PROJECT
                  AND prof2.actionUnit.createdByInstitution = i
            )
            """)
    Set<Institution> findAllVisibleToPerson(Long personId,
                                            Collection<String> instancePermissionCodes,
                                            String organizationAccessCode);

}
