package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.phase.CustomFieldSelectMultiplePhase;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionCodeRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.*;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FieldAnswerPatchServiceTest {

    private static final long PROJECT_ID = 7L;

    @Mock
    private FormService formService;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private PhaseRepository phaseRepository;
    @Mock
    private PhaseMapper phaseMapper;

    private FieldAnswerPatchService service;
    private final PhaseDTO entity = new PhaseDTO();

    @BeforeEach
    void setUp() {
        service = new FieldAnswerPatchService(formService, conceptRepository, mock(ConceptMapper.class),
                mock(PersonRepository.class), mock(PersonMapper.class), mock(ActionUnitRepository.class),
                mock(ActionUnitSummaryMapper.class), mock(ActionCodeRepository.class), mock(ActionCodeMapper.class),
                mock(SpatialUnitRepository.class), mock(SpatialUnitSummaryMapper.class),
                mock(RecordingUnitRepository.class), mock(RecordingUnitSummaryMapper.class),
                phaseRepository, phaseMapper, mock(ContainerRepository.class), mock(ContainerMapper.class),
                mock(SpecimenRepository.class), mock(SpecimenSummaryMapper.class), mock(UnitDefinitionMapper.class));
    }

    @Test
    void apply_returnsOnlyTheAdditionalFieldsItTouched_andWritesTheSystemOnesOntoTheEntity() {
        CustomFieldText system = text(1L, true);
        CustomFieldText additional = text(2L, false);
        CustomFieldText untouched = text(3L, false);
        CustomFieldAnswerTextViewModel additionalVm = new CustomFieldAnswerTextViewModel();
        CustomFormResponseViewModel response = givenResponse(Map.of(
                system, new CustomFieldAnswerTextViewModel(),
                additional, additionalVm,
                untouched, new CustomFieldAnswerTextViewModel()));

        Map<CustomField, CustomFieldAnswerViewModel> result = service.apply(entity, form(system, additional, untouched),
                Map.of("1", new AnswerInput("titre", null), "2", new AnswerInput("note", null)), PROJECT_ID);

        assertThat(result).containsOnlyKeys(additional);
        assertThat(result.get(additional)).isSameAs(additionalVm);
        verify(formService).updateJpaEntityFromResponse(response, entity);
    }

    @Test
    void apply_clearsASystemFieldSentAsNull() {
        CustomFieldText system = text(1L, true);
        givenResponse(Map.of(system, new CustomFieldAnswerTextViewModel()));

        Map<String, AnswerInput> answers = new HashMap<>();
        answers.put("1", new AnswerInput(null, null));
        service.apply(entity, form(system), answers, PROJECT_ID);

        verify(formService).applyTypedValueToAnswer(any(), isNull());
        verify(formService).clearSystemField(entity, system);
    }

    @Test
    void apply_leavesAMultiValueFieldAlone_whenValuesIsNull_butClearsItOnAnEmptyList() {
        CustomFieldSelectMultipleFromFieldCode concepts = new CustomFieldSelectMultipleFromFieldCode();
        concepts.setId(4L);
        concepts.setIsSystemField(false);
        givenResponse(Map.of(concepts, new CustomFieldAnswerSelectMultipleFromFieldCodeViewModel()));

        service.apply(entity, form(concepts), Map.of("4", new AnswerInput(null, null)), PROJECT_ID);
        verify(formService, never()).applyTypedValueToAnswer(any(), any());

        service.apply(entity, form(concepts), Map.of("4", new AnswerInput(null, List.of())), PROJECT_ID);
        verify(formService).applyTypedValueToAnswer(any(), eq(List.of()));
    }

    @Test
    void apply_takesABareDateAtMidnightUtc_andADecimalWithAComma() {
        CustomFieldDateTime date = new CustomFieldDateTime();
        date.setId(5L);
        CustomFieldDecimal decimal = new CustomFieldDecimal();
        decimal.setId(6L);
        givenResponse(Map.of(date, new CustomFieldAnswerDateTimeViewModel(), decimal, new CustomFieldAnswerDecimalViewModel()));

        service.apply(entity, form(date, decimal),
                Map.of("5", new AnswerInput("2026-09-25", null), "6", new AnswerInput("12,5", null)), PROJECT_ID);

        verify(formService).applyTypedValueToAnswer(any(), eq(OffsetDateTime.of(2026, 9, 25, 0, 0, 0, 0, ZoneOffset.UTC)));
        verify(formService).applyTypedValueToAnswer(any(), eq(12.5));
    }

    @Test
    void apply_rejectsAPhaseOfAnotherProject() {
        CustomFieldSelectMultiplePhase phases = new CustomFieldSelectMultiplePhase();
        phases.setId(8L);
        givenResponse(Map.of(phases, new CustomFieldAnswerSelectMultiplePhaseViewModel()));
        Phase elsewhere = new Phase();
        ActionUnit otherProject = new ActionUnit();
        otherProject.setId(99L);
        elsewhere.setActionUnit(otherProject);
        when(phaseRepository.findById(30L)).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.apply(entity, form(phases), Map.of("8", new AnswerInput(null, List.of(30))), PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void apply_rejectsAFieldAbsentFromTheForm_whileApplyLenientSkipsIt() {
        CustomFieldText known = text(1L, false);
        givenResponse(Map.of(known, new CustomFieldAnswerTextViewModel()));
        Map<String, AnswerInput> answers = Map.of("1", new AnswerInput("ok", null), "404", new AnswerInput("x", null));

        assertThatThrownBy(() -> service.apply(entity, form(known), answers, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class);

        Map<CustomField, CustomFieldAnswerViewModel> lenient = service.applyLenient(entity, form(known), answers, PROJECT_ID);
        assertThat(lenient).containsOnlyKeys(known);
    }

    @Test
    void apply_rejectsAnUnparsableValue_whileApplyLenientSkipsIt() {
        CustomFieldDecimal decimal = new CustomFieldDecimal();
        decimal.setId(6L);
        givenResponse(Map.of(decimal, new CustomFieldAnswerDecimalViewModel()));
        Map<String, AnswerInput> answers = Map.of("6", new AnswerInput("douze", null));

        assertThatThrownBy(() -> service.apply(entity, form(decimal), answers, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class);

        service.applyLenient(entity, form(decimal), answers, PROJECT_ID);
        verify(formService, never()).applyTypedValueToAnswer(any(), any());
    }

    // ========== Helpers ==========

    private CustomFormResponseViewModel givenResponse(Map<CustomField, CustomFieldAnswerViewModel> answers) {
        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        response.setAnswers(new HashMap<>(answers));
        lenient().when(formService.initOrReuseResponse(isNull(), same(entity), any(), eq(true))).thenReturn(response);
        return response;
    }

    private static CustomFieldText text(long id, boolean system) {
        CustomFieldText field = new CustomFieldText();
        field.setId(id);
        field.setIsSystemField(system);
        field.setValueBinding(system ? "title" : null);
        return field;
    }

    private static FormUiDto form(CustomField... fields) {
        List<CustomColUiDto> columns = new ArrayList<>();
        for (CustomField field : fields) {
            CustomColUiDto col = new CustomColUiDto();
            col.setField(field);
            columns.add(col);
        }
        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(columns);
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));
        FormUiDto form = new FormUiDto();
        form.setLayout(List.of(panel));
        return form;
    }
}
