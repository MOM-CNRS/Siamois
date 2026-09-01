package fr.siamois.infrastructure.database.repositories.institution;


import fr.siamois.domain.models.institution.Institution;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface InstitutionRepository extends CrudRepository<Institution, Long>, RevisionRepository<Institution, Long, Long> {

    Optional<Institution> findInstitutionByIdentifier(@NotNull String identifier);

    @Query("""
            SELECT COUNT(a) > 0
            FROM PersonProfileAssignment a
            JOIN a.profile prof
            WHERE a.person.id = :personId
              AND prof.institution.id = :institutionId
              AND prof.code = fr.siamois.domain.models.permissions.ProfileConstants.ORGANIZATION_MANAGER
            """)
    boolean personIsInstitutionManagerOf(Long institutionId, Long personId);

    /**
     * Institutions the person is allowed to display, based on the profile permission system:
     * an INSTANCE-scoped profile holding {@code ORGANIZATION_ACCESS} grants every institution of the
     * instance (this is how the superadmin reaches organizations they are not a member of), while an
     * ORGANISATION-scoped profile grants its institution and a PROJECT-scoped profile grants the
     * institution owning its action unit.
     */
    @Query("""
            SELECT DISTINCT i FROM Institution i
            WHERE EXISTS (SELECT 1 FROM PersonProfileAssignment a
                          JOIN a.profile prof
                          JOIN prof.permissions perm
                          WHERE a.person.id = :personId
                            AND prof.scope = fr.siamois.domain.models.permissions.PermissionScopeType.INSTANCE
                            AND perm.code = fr.siamois.domain.models.permissions.PermissionConstants.ORGANIZATION_ACCESS)
               OR EXISTS (SELECT 1 FROM PersonProfileAssignment a
                          JOIN a.profile prof
                          WHERE a.person.id = :personId
                            AND prof.institution.id = i.id)
            """)
    Set<Institution> findAllVisibleToPerson(Long personId);

    List<Institution> findAllByIdentifierIn(Collection<String> identifiers);

    Optional<Institution> findByIdentifierIgnoreCase(String identifier);
}
