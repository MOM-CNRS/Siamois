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
            value = "SELECT recording_unit_nextval(:parentRecordingUnitId, :conceptTypeId)"
    )
    int nextIdAndIncrement(Long parentRecordingUnitId, Long conceptTypeId);

    Optional<RecordingUnitIdCounter> findByConfigActionUnitAndRecordingUnitTypeAndRecordingUnit(ActionUnit configActionUnit, Concept recordingUnitType, RecordingUnit recordingUnit);
}
