package fr.siamois.infrastructure.database.repositories.person;

import fr.siamois.domain.models.auth.pending.PendingInstitutionInvite;
import fr.siamois.domain.models.auth.pending.PendingPerson;
import fr.siamois.domain.models.institution.Institution;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface PendingInstitutionInviteRepository extends CrudRepository<PendingInstitutionInvite, Long> {
    Optional<PendingInstitutionInvite> findByInstitutionAndPendingPerson(Institution institution, PendingPerson pendingPerson);

    Set<PendingInstitutionInvite> findAllByPendingPerson(PendingPerson pendingPerson);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM pending_invite_profile WHERE fk_profile_id IN " +
            "(SELECT profile_id FROM profile WHERE fk_action_unit_id = :actionUnitId)")
    void deleteProfileLinksByProfileActionUnitId(@Param("actionUnitId") Long actionUnitId);
}
