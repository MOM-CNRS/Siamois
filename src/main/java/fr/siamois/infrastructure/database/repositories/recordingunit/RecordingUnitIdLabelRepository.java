package fr.siamois.infrastructure.database.repositories.recordingunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdLabel;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecordingUnitIdLabelRepository extends CrudRepository<RecordingUnitIdLabel, Long> {
    Optional<RecordingUnitIdLabel> findByTypeAndActionUnit(Concept type, ActionUnit actionUnit);

    Optional<RecordingUnitIdLabel> findByExistingAndActionUnit(String existing, ActionUnit actionUnit);
}
