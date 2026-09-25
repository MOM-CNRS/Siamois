package fr.siamois.domain.services.form;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigAnswerRepository;
import fr.siamois.mapper.*;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The pivot row between an entity and its additional-field answers. There is one per (entity, form
 * config): answers belong to the entity and every user reads and writes the same set. The row's
 * person is only its last author — it is refreshed on every {@code createOrGet…}, which the save
 * path calls right before writing answers.
 */
@Service
@RequiredArgsConstructor
public class FormConfigAnswerService {

    private final PersonMapper personMapper;
    private final RecordingUnitMapper recordingUnitMapper;
    private final FormConfigAnswerRepository formConfigAnswerRepository;
    private final SpecimenMapper specimenMapper;
    private final PhaseMapper phaseMapper;
    private final ContainerMapper containerMapper;

    /**
     * Read-only lookup of the pivot row for a recording unit, unlike
     * {@link #createOrGetFormConfigAnswer(FormConfig, RecordingUnitDTO)}: used to load previously
     * saved additional-field answers back into the form, where opening a form must not create rows.
     */
    public Optional<FormConfigAnswer> findFormConfigAnswer(FormConfig formConfig, RecordingUnitDTO recordingUnitDTO) {
        return formConfigAnswerRepository.findByFormConfigAndRecordingUnit(formConfig, recordingUnitMapper.invertConvert(recordingUnitDTO));
    }

    public Optional<FormConfigAnswer> findFormConfigAnswer(FormConfig formConfig, SpecimenDTO specimenDTO) {
        return formConfigAnswerRepository.findByFormConfigAndSpecimen(formConfig, specimenMapper.invertConvert(specimenDTO));
    }

    public Optional<FormConfigAnswer> findFormConfigAnswer(FormConfig formConfig, PhaseDTO phaseDTO) {
        return formConfigAnswerRepository.findByFormConfigAndPhase(formConfig, phaseMapper.invertConvert(phaseDTO));
    }

    public Optional<FormConfigAnswer> findFormConfigAnswer(FormConfig formConfig, ContainerDTO containerDTO) {
        return formConfigAnswerRepository.findByFormConfigAndContainer(formConfig, containerMapper.invertConvert(containerDTO));
    }

    /** Every answer set of the recording unit, whichever type's form config each was saved under. */
    public List<FormConfigAnswer> findAllFormConfigAnswers(RecordingUnitDTO recordingUnitDTO) {
        return formConfigAnswerRepository.findByRecordingUnit(recordingUnitMapper.invertConvert(recordingUnitDTO));
    }

    public List<FormConfigAnswer> findAllFormConfigAnswers(SpecimenDTO specimenDTO) {
        return formConfigAnswerRepository.findBySpecimen(specimenMapper.invertConvert(specimenDTO));
    }

    public List<FormConfigAnswer> findAllFormConfigAnswers(PhaseDTO phaseDTO) {
        return formConfigAnswerRepository.findByPhase(phaseMapper.invertConvert(phaseDTO));
    }

    public List<FormConfigAnswer> findAllFormConfigAnswers(ContainerDTO containerDTO) {
        return formConfigAnswerRepository.findByContainer(containerMapper.invertConvert(containerDTO));
    }

    /** Removes an answer set; its answers must already be deleted. */
    public void delete(FormConfigAnswer formConfigAnswer) {
        formConfigAnswerRepository.delete(formConfigAnswer);
    }

    public FormConfigAnswer createOrGetFormConfigAnswer(FormConfig formConfig, RecordingUnitDTO recordingUnitDTO) {
        RecordingUnit recordingUnit = recordingUnitMapper.invertConvert(recordingUnitDTO);
        return createOrGet(formConfig,
                () -> formConfigAnswerRepository.findByFormConfigAndRecordingUnit(formConfig, recordingUnit),
                row -> row.setRecordingUnit(recordingUnit));
    }

    public FormConfigAnswer createOrGetFormConfigAnswer(FormConfig formConfig, SpecimenDTO specimenDTO) {
        Specimen specimen = specimenMapper.invertConvert(specimenDTO);
        return createOrGet(formConfig,
                () -> formConfigAnswerRepository.findByFormConfigAndSpecimen(formConfig, specimen),
                row -> row.setSpecimen(specimen));
    }

    public FormConfigAnswer createOrGetFormConfigAnswer(FormConfig formConfig, PhaseDTO phaseDTO) {
        Phase phase = phaseMapper.invertConvert(phaseDTO);
        return createOrGet(formConfig,
                () -> formConfigAnswerRepository.findByFormConfigAndPhase(formConfig, phase),
                row -> row.setPhase(phase));
    }

    public FormConfigAnswer createOrGetFormConfigAnswer(FormConfig formConfig, ContainerDTO containerDTO) {
        Container container = containerMapper.invertConvert(containerDTO);
        return createOrGet(formConfig,
                () -> formConfigAnswerRepository.findByFormConfigAndContainer(formConfig, container),
                row -> row.setContainer(container));
    }

    private FormConfigAnswer createOrGet(FormConfig formConfig,
                                         Supplier<Optional<FormConfigAnswer>> existing,
                                         Consumer<FormConfigAnswer> linkEntity) {
        UserInfo info = ExecutionContextHolder.getNonNull();
        Person author = personMapper.invertConvert(info.getUser());

        FormConfigAnswer row = existing.get().orElseGet(() -> {
            FormConfigAnswer created = new FormConfigAnswer();
            created.setFormConfig(formConfig);
            linkEntity.accept(created);
            return created;
        });
        row.setPerson(author);
        return formConfigAnswerRepository.save(row);
    }

}
