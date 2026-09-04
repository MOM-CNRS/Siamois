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
     * Institutions the person is allowed to display: those carrying a profile assigned to the person,
     * whether ORGANISATION-scoped (the institution itself) or PROJECT-scoped (the institution owning
     * the action unit). INSTANCE-scoped profiles carry no institution and grant nothing here — the
     * superadmin sees an organization because they are assigned its
     * {@link fr.siamois.domain.models.permissions.ProfileConstants#ORGANIZATION_MANAGER} profile like
     * any other manager.
     */
    @Query("""
            SELECT DISTINCT i FROM PersonProfileAssignment a
                        JOIN a.profile prof
                        JOIN prof.institution i
                        WHERE a.person.id = :personId
            """)
    Set<Institution> findAllVisibleToPerson(Long personId);

    List<Institution> findAllByIdentifierIn(Collection<String> identifiers);

    Optional<Institution> findByIdentifierIgnoreCase(String identifier);
}
