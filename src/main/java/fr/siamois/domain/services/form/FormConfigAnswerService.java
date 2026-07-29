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

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FormConfigAnswerService {

    private final PersonMapper personMapper;
    private final RecordingUnitMapper recordingUnitMapper;
    private final FormConfigAnswerRepository formConfigAnswerRepository;
    private final SpecimenMapper specimenMapper;
    private final PhaseMapper phaseMapper;
    private final ContainerMapper containerMapper;

    public FormConfigAnswer createOrGetFormConfigAnswer(FormConfig formConfig, RecordingUnitDTO recordingUnitDTO) {
        UserInfo info = ExecutionContextHolder.get();
        assert info != null;
        RecordingUnit recordingUnit = recordingUnitMapper.invertConvert(recordingUnitDTO);
        Person person = personMapper.invertConvert(info.getUser());

        Optional<FormConfigAnswer> opt = formConfigAnswerRepository.findByFormConfigAndPersonAndRecordingUnit(formConfig, person, recordingUnit);
        if (opt.isPresent()) {
            return opt.get();
        }

        FormConfigAnswer formConfigAnswer = new FormConfigAnswer();
        formConfigAnswer.setFormConfig(formConfig);
        formConfigAnswer.setRecordingUnit(recordingUnit);
        formConfigAnswer.setPerson(person);

        return formConfigAnswerRepository.save(formConfigAnswer);
    }

    public FormConfigAnswer createOrGetFormConfigAnswer(FormConfig formConfig, SpecimenDTO specimenDTO) {
        UserInfo info = ExecutionContextHolder.get();
        assert info != null;
        Specimen specimen = specimenMapper.invertConvert(specimenDTO);
        Person person = personMapper.invertConvert(info.getUser());

        Optional<FormConfigAnswer> opt = formConfigAnswerRepository.findByFormConfigAndPersonAndSpecimen(formConfig, person, specimen);
        if (opt.isPresent()) {
            return opt.get();
        }

        FormConfigAnswer formConfigAnswer = new FormConfigAnswer();
        formConfigAnswer.setFormConfig(formConfig);
        formConfigAnswer.setSpecimen(specimen);
        formConfigAnswer.setPerson(person);

        return formConfigAnswerRepository.save(formConfigAnswer);
    }

    public FormConfigAnswer createOrGetFormConfigAnswer(FormConfig formConfig, PhaseDTO phaseDTO) {
        UserInfo info = ExecutionContextHolder.get();
        assert info != null;
        Phase phase = phaseMapper.invertConvert(phaseDTO);
        Person person = personMapper.invertConvert(info.getUser());

        Optional<FormConfigAnswer> opt = formConfigAnswerRepository.findByFormConfigAndPersonAndPhase(formConfig, person, phase);
        if (opt.isPresent()) {
            return opt.get();
        }

        FormConfigAnswer formConfigAnswer = new FormConfigAnswer();
        formConfigAnswer.setFormConfig(formConfig);
        formConfigAnswer.setPhase(phase);
        formConfigAnswer.setPerson(person);

        return formConfigAnswerRepository.save(formConfigAnswer);
    }

    public FormConfigAnswer createOrGetFormConfigAnswer(FormConfig formConfig, ContainerDTO containerDTO) {
        UserInfo info = ExecutionContextHolder.get();
        assert info != null;
        Container container = containerMapper.invertConvert(containerDTO);
        Person person = personMapper.invertConvert(info.getUser());

        Optional<FormConfigAnswer> opt = formConfigAnswerRepository.findByFormConfigAndPersonAndContainer(formConfig, person, container);
        if (opt.isPresent()) {
            return opt.get();
        }

        FormConfigAnswer formConfigAnswer = new FormConfigAnswer();
        formConfigAnswer.setFormConfig(formConfig);
        formConfigAnswer.setContainer(container);
        formConfigAnswer.setPerson(person);

        return formConfigAnswerRepository.save(formConfigAnswer);
    }

}
