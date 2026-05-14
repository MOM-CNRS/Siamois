package fr.siamois.infrastructure.database.repositories.recordingunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdCounter;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecordingUnitIdCounterRepository extends CrudRepository<RecordingUnitIdCounter, Long> {

    @Query(
            nativeQuery = true,
            value = "SELECT r.* FROM ru_nextval_unique(:actionUnitId) r"
    )
    int ruNextValUnique(Long actionUnitId);

    @Query(
            nativeQuery = true,
            value = "SELECT r.* FROM ru_nextval_parent(:parentRecordingUnitId) r"
    )
    int ruNextValParent(Long parentRecordingUnitId);

    @Query(
            nativeQuery = true,
            value = "SELECT r.* FROM ru_nextval_type_unique(:actionUnitId, :conceptTypeId) r"
    )
    int ruNextValTypeUnique(Long actionUnitId, Long conceptTypeId);

    @Query(
            nativeQuery = true,
            value = "SELECT r.* FROM ru_nextval_type_parent(:parentRecordingUnitId, :conceptTypeId) r"
    )
    int ruNextValTypeParent(Long parentRecordingUnitId, Long conceptTypeId);

    Optional<RecordingUnitIdCounter> findByConfigActionUnitAndRecordingUnitTypeAndRecordingUnit(ActionUnit configActionUnit, Concept recordingUnitType, RecordingUnit recordingUnit);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM identifier_ru_counter WHERE fk_action_unit_id = :actionUnitId")
    void deleteAllByConfigActionUnitId(@Param("actionUnitId") Long actionUnitId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM identifier_ru_counter WHERE fk_recording_unit_id = :recordingUnitId")
    void deleteAllByRecordingUnitId(@Param("recordingUnitId") Long recordingUnitId);
}
