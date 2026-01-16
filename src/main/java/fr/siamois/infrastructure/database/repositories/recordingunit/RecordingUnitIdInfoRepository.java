package fr.siamois.infrastructure.database.repositories.recordingunit;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecordingUnitIdInfoRepository extends CrudRepository<RecordingUnitIdInfo, Long> {
}
