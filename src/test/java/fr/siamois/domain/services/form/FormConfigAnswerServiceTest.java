package fr.siamois.domain.services.form;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigAnswerRepository;
import fr.siamois.mapper.*;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormConfigAnswerServiceTest {

    @Mock
    private PersonMapper personMapper;
    @Mock
    private RecordingUnitMapper recordingUnitMapper;
    @Mock
    private FormConfigAnswerRepository formConfigAnswerRepository;
    @Mock
    private SpecimenMapper specimenMapper;
    @Mock
    private PhaseMapper phaseMapper;
    @Mock
    private ContainerMapper containerMapper;

    @InjectMocks
    private FormConfigAnswerService service;

    private FormConfig formConfig;
    private PersonDTO userDTO;
    private Person person;
    private FormConfigAnswer stored;

    @BeforeEach
    void setUp() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        userDTO = new PersonDTO();
        userDTO.setId(2L);
        ExecutionContextHolder.set(new UserInfo(institution, userDTO, "fr"));

        formConfig = new FormConfig();
        formConfig.setId(3L);
        person = new Person();
        person.setId(2L);

        // What the repository hands back on save: a distinct instance, so the tests can tell the
        // returned answer comes from the repository and not from the service's local one.
        stored = new FormConfigAnswer();
        stored.setId(99L);
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    // ========== Recording unit ==========

    @Test
    void createOrGetFormConfigAnswer_shouldReturnTheAnswerTheUserAlreadyHasOnARecordingUnit() {
        RecordingUnitDTO recordingUnitDTO = new RecordingUnitDTO();
        RecordingUnit recordingUnit = new RecordingUnit();
        givenCurrentPerson();
        when(recordingUnitMapper.invertConvert(recordingUnitDTO)).thenReturn(recordingUnit);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndRecordingUnit(formConfig, person, recordingUnit))
                .thenReturn(Optional.of(stored));

        FormConfigAnswer answer = service.createOrGetFormConfigAnswer(formConfig, recordingUnitDTO);

        assertThat(answer).isSameAs(stored);
        verify(formConfigAnswerRepository, never()).save(any());
    }

    @Test
    void createOrGetFormConfigAnswer_shouldCreateTheAnswerOfARecordingUnitThatHasNone() {
        RecordingUnitDTO recordingUnitDTO = new RecordingUnitDTO();
        RecordingUnit recordingUnit = new RecordingUnit();
        givenCurrentPerson();
        when(recordingUnitMapper.invertConvert(recordingUnitDTO)).thenReturn(recordingUnit);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndRecordingUnit(formConfig, person, recordingUnit))
                .thenReturn(Optional.empty());
        when(formConfigAnswerRepository.save(any(FormConfigAnswer.class))).thenReturn(stored);

        FormConfigAnswer answer = service.createOrGetFormConfigAnswer(formConfig, recordingUnitDTO);

        assertThat(answer).isSameAs(stored);
        FormConfigAnswer created = capturedSavedAnswer();
        assertThat(created.getFormConfig()).isSameAs(formConfig);
        assertThat(created.getPerson()).isSameAs(person);
        assertThat(created.getRecordingUnit()).isSameAs(recordingUnit);
        assertThatItHasASingleOwner(created);
    }

    // ========== findFormConfigAnswer (read-only) ==========

    @Test
    void findFormConfigAnswer_shouldReturnTheExistingAnswer() {
        RecordingUnitDTO recordingUnitDTO = new RecordingUnitDTO();
        RecordingUnit recordingUnit = new RecordingUnit();
        givenCurrentPerson();
        when(recordingUnitMapper.invertConvert(recordingUnitDTO)).thenReturn(recordingUnit);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndRecordingUnit(formConfig, person, recordingUnit))
                .thenReturn(Optional.of(stored));

        Optional<FormConfigAnswer> answer = service.findFormConfigAnswer(formConfig, recordingUnitDTO);

        assertThat(answer).contains(stored);
        verify(formConfigAnswerRepository, never()).save(any());
    }

    @Test
    void findFormConfigAnswer_shouldNotCreateAnAnswerWhenNoneExists() {
        RecordingUnitDTO recordingUnitDTO = new RecordingUnitDTO();
        RecordingUnit recordingUnit = new RecordingUnit();
        givenCurrentPerson();
        when(recordingUnitMapper.invertConvert(recordingUnitDTO)).thenReturn(recordingUnit);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndRecordingUnit(formConfig, person, recordingUnit))
                .thenReturn(Optional.empty());

        Optional<FormConfigAnswer> answer = service.findFormConfigAnswer(formConfig, recordingUnitDTO);

        assertThat(answer).isEmpty();
        verify(formConfigAnswerRepository, never()).save(any());
    }

    // ========== Specimen ==========

    @Test
    void createOrGetFormConfigAnswer_shouldReturnTheAnswerTheUserAlreadyHasOnASpecimen() {
        SpecimenDTO specimenDTO = new SpecimenDTO();
        Specimen specimen = new Specimen();
        givenCurrentPerson();
        when(specimenMapper.invertConvert(specimenDTO)).thenReturn(specimen);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndSpecimen(formConfig, person, specimen))
                .thenReturn(Optional.of(stored));

        FormConfigAnswer answer = service.createOrGetFormConfigAnswer(formConfig, specimenDTO);

        assertThat(answer).isSameAs(stored);
        verify(formConfigAnswerRepository, never()).save(any());
    }

    @Test
    void createOrGetFormConfigAnswer_shouldCreateTheAnswerOfASpecimenThatHasNone() {
        SpecimenDTO specimenDTO = new SpecimenDTO();
        Specimen specimen = new Specimen();
        givenCurrentPerson();
        when(specimenMapper.invertConvert(specimenDTO)).thenReturn(specimen);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndSpecimen(formConfig, person, specimen))
                .thenReturn(Optional.empty());
        when(formConfigAnswerRepository.save(any(FormConfigAnswer.class))).thenReturn(stored);

        FormConfigAnswer answer = service.createOrGetFormConfigAnswer(formConfig, specimenDTO);

        assertThat(answer).isSameAs(stored);
        FormConfigAnswer created = capturedSavedAnswer();
        assertThat(created.getFormConfig()).isSameAs(formConfig);
        assertThat(created.getPerson()).isSameAs(person);
        assertThat(created.getSpecimen()).isSameAs(specimen);
        assertThatItHasASingleOwner(created);
    }

    // ========== Phase ==========

    @Test
    void createOrGetFormConfigAnswer_shouldReturnTheAnswerTheUserAlreadyHasOnAPhase() {
        PhaseDTO phaseDTO = new PhaseDTO();
        Phase phase = new Phase();
        givenCurrentPerson();
        when(phaseMapper.invertConvert(phaseDTO)).thenReturn(phase);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndPhase(formConfig, person, phase))
                .thenReturn(Optional.of(stored));

        FormConfigAnswer answer = service.createOrGetFormConfigAnswer(formConfig, phaseDTO);

        assertThat(answer).isSameAs(stored);
        verify(formConfigAnswerRepository, never()).save(any());
    }

    @Test
    void createOrGetFormConfigAnswer_shouldCreateTheAnswerOfAPhaseThatHasNone() {
        PhaseDTO phaseDTO = new PhaseDTO();
        Phase phase = new Phase();
        givenCurrentPerson();
        when(phaseMapper.invertConvert(phaseDTO)).thenReturn(phase);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndPhase(formConfig, person, phase))
                .thenReturn(Optional.empty());
        when(formConfigAnswerRepository.save(any(FormConfigAnswer.class))).thenReturn(stored);

        FormConfigAnswer answer = service.createOrGetFormConfigAnswer(formConfig, phaseDTO);

        assertThat(answer).isSameAs(stored);
        FormConfigAnswer created = capturedSavedAnswer();
        assertThat(created.getFormConfig()).isSameAs(formConfig);
        assertThat(created.getPerson()).isSameAs(person);
        assertThat(created.getPhase()).isSameAs(phase);
        assertThatItHasASingleOwner(created);
    }

    // ========== Container ==========

    @Test
    void createOrGetFormConfigAnswer_shouldReturnTheAnswerTheUserAlreadyHasOnAContainer() {
        ContainerDTO containerDTO = new ContainerDTO();
        Container container = new Container();
        givenCurrentPerson();
        when(containerMapper.invertConvert(containerDTO)).thenReturn(container);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndContainer(formConfig, person, container))
                .thenReturn(Optional.of(stored));

        FormConfigAnswer answer = service.createOrGetFormConfigAnswer(formConfig, containerDTO);

        assertThat(answer).isSameAs(stored);
        verify(formConfigAnswerRepository, never()).save(any());
    }

    @Test
    void createOrGetFormConfigAnswer_shouldCreateTheAnswerOfAContainerThatHasNone() {
        ContainerDTO containerDTO = new ContainerDTO();
        Container container = new Container();
        givenCurrentPerson();
        when(containerMapper.invertConvert(containerDTO)).thenReturn(container);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndContainer(formConfig, person, container))
                .thenReturn(Optional.empty());
        when(formConfigAnswerRepository.save(any(FormConfigAnswer.class))).thenReturn(stored);

        FormConfigAnswer answer = service.createOrGetFormConfigAnswer(formConfig, containerDTO);

        assertThat(answer).isSameAs(stored);
        FormConfigAnswer created = capturedSavedAnswer();
        assertThat(created.getFormConfig()).isSameAs(formConfig);
        assertThat(created.getPerson()).isSameAs(person);
        assertThat(created.getContainer()).isSameAs(container);
        assertThatItHasASingleOwner(created);
    }

    // ========== The answer belongs to the user bound to the thread ==========

    @Test
    void createOrGetFormConfigAnswer_shouldAnswerAsTheUserBoundToTheThread() {
        PersonDTO otherUserDTO = new PersonDTO();
        otherUserDTO.setId(50L);
        Person otherPerson = new Person();
        otherPerson.setId(50L);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        ExecutionContextHolder.set(new UserInfo(institution, otherUserDTO, "fr"));

        RecordingUnitDTO recordingUnitDTO = new RecordingUnitDTO();
        RecordingUnit recordingUnit = new RecordingUnit();
        when(personMapper.invertConvert(otherUserDTO)).thenReturn(otherPerson);
        when(recordingUnitMapper.invertConvert(recordingUnitDTO)).thenReturn(recordingUnit);
        when(formConfigAnswerRepository.findByFormConfigAndPersonAndRecordingUnit(formConfig, otherPerson, recordingUnit))
                .thenReturn(Optional.empty());
        when(formConfigAnswerRepository.save(any(FormConfigAnswer.class))).thenReturn(stored);

        service.createOrGetFormConfigAnswer(formConfig, recordingUnitDTO);

        assertThat(capturedSavedAnswer().getPerson()).isSameAs(otherPerson);
    }

    /**
     * The service reads its author from the thread and asserts it is there. With assertions enabled
     * — the default of the surefire plugin — that is an {@link AssertionError}; without them the
     * null user blows up on the next line, hence the two accepted types.
     */
    @Test
    void createOrGetFormConfigAnswer_shouldFailWhenNoUserIsBoundToTheThread() {
        ExecutionContextHolder.clear();
        RecordingUnitDTO recordingUnitDTO = new RecordingUnitDTO();

        assertThatThrownBy(() -> service.createOrGetFormConfigAnswer(formConfig, recordingUnitDTO))
                .isInstanceOfAny(AssertionError.class, NullPointerException.class);

        verify(formConfigAnswerRepository, never()).save(any());
    }

    // ========== Helpers ==========

    private void givenCurrentPerson() {
        when(personMapper.invertConvert(userDTO)).thenReturn(person);
    }

    private FormConfigAnswer capturedSavedAnswer() {
        ArgumentCaptor<FormConfigAnswer> saved = ArgumentCaptor.forClass(FormConfigAnswer.class);
        verify(formConfigAnswerRepository).save(saved.capture());
        return saved.getValue();
    }

    /**
     * A {@code FormConfigAnswer} carries exactly one owner among the four; the overloads are copies
     * of one another, so this guards the paste error of also setting somebody else's.
     */
    private void assertThatItHasASingleOwner(FormConfigAnswer answer) {
        assertThat(Stream.of(answer.getRecordingUnit(), answer.getSpecimen(), answer.getPhase(), answer.getContainer())
                .filter(Objects::nonNull))
                .hasSize(1);
    }
}
