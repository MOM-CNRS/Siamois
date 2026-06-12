package fr.siamois.infrastructure.database.repositories.person;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.pending.PendingActionUnitAttribution;
import fr.siamois.domain.models.auth.pending.PendingInstitutionInvite;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface PendingActionUnitRepository extends CrudRepository<PendingActionUnitAttribution, PendingActionUnitAttribution.PendingActionUnitId> {
    Optional<PendingActionUnitAttribution> findByActionUnitAndInstitutionInvite(ActionUnit actionUnit, PendingInstitutionInvite institutionInvite);

    Set<PendingActionUnitAttribution> findByInstitutionInvite(PendingInstitutionInvite institutionInvite);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM pending_action_unit_attribution WHERE fk_action_unit_id = :actionUnitId")
    void deleteAllByActionUnitId(@Param("actionUnitId") Long actionUnitId);
}
