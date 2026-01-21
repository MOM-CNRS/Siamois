package fr.siamois.infrastructure.database.repositories.recordingunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdCounter;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
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
}
