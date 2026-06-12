package fr.siamois.infrastructure.database.repositories.recordingunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdLabel;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecordingUnitIdLabelRepository extends CrudRepository<RecordingUnitIdLabel, Long> {
    Optional<RecordingUnitIdLabel> findByTypeAndActionUnit(Concept type, ActionUnit actionUnit);

    Optional<RecordingUnitIdLabel> findByExistingAndActionUnit(String existing, ActionUnit actionUnit);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM identifier_ru_label WHERE fk_action_unit_id = :actionUnitId")
    void deleteAllByActionUnitId(@Param("actionUnitId") Long actionUnitId);
}
