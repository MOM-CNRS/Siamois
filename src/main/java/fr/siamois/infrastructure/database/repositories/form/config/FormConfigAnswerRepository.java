package fr.siamois.infrastructure.database.repositories.form.config;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FormConfigAnswerRepository extends CrudRepository<FormConfigAnswer, Long> {
    Optional<FormConfigAnswer> findByFormConfigAndPersonAndRecordingUnit(FormConfig formConfig, Person person, RecordingUnit recordingUnit);

    Optional<FormConfigAnswer> findByFormConfigAndPersonAndSpecimen(FormConfig formConfig, Person person, Specimen specimen);

    Optional<FormConfigAnswer> findByFormConfigAndPersonAndPhase(FormConfig formConfig, Person person, Phase phase);

    Optional<FormConfigAnswer> findByFormConfigAndPersonAndContainer(FormConfig formConfig, Person person, Container container);
}
