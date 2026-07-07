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
     * an INSTANCE-scoped profile holding one of {@code instancePermissionCodes} grants every institution,
     * an ORGANISATION-scoped profile holding {@code organizationAccessCode} grants its institution,
     * and a PROJECT-scoped profile grants the institution owning its action unit.
     */
    @Query("""
            SELECT DISTINCT i FROM PersonProfileAssignment a
                        JOIN a.profile prof
                        JOIN prof.institution i
                        WHERE a.person.id = :personId
            """)
    Set<Institution> findAllVisibleToPerson(Long personId);

    List<Institution> findAllByIdentifierIn(Collection<String> identifiers);
}
