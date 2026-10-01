package fr.siamois.infrastructure.database.repositories.form.config;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormConfigAnswerRepository extends CrudRepository<FormConfigAnswer, Long> {
    // No person in the key: an answer set belongs to its entity, whoever last wrote it
    // (FormConfigAnswer's own UNIQUE(entity, form config) guarantees at most one row).
    Optional<FormConfigAnswer> findByFormConfigAndRecordingUnit(FormConfig formConfig, RecordingUnit recordingUnit);

    Optional<FormConfigAnswer> findByFormConfigAndSpecimen(FormConfig formConfig, Specimen specimen);

    Optional<FormConfigAnswer> findByFormConfigAndPhase(FormConfig formConfig, Phase phase);

    Optional<FormConfigAnswer> findByFormConfigAndContainer(FormConfig formConfig, Container container);

    // Every answer set of an entity, whatever its form config: more than one only while answers
    // saved under a former type are still around (see CustomFieldAnswerService's type change).
    List<FormConfigAnswer> findByRecordingUnit(RecordingUnit recordingUnit);

    List<FormConfigAnswer> findBySpecimen(Specimen specimen);

    List<FormConfigAnswer> findByPhase(Phase phase);

    List<FormConfigAnswer> findByContainer(Container container);
}
