package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.phase.CustomFieldSelectMultiplePhase;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.rules.Condition;
import fr.siamois.domain.models.form.rules.FieldConstraint;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
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
    @Mock
    private ConceptMapper conceptMapper;

    private FieldAnswerPatchService service;
    private final PhaseDTO entity = new PhaseDTO();

    @BeforeEach
    void setUp() {
        service = new FieldAnswerPatchService(formService, conceptRepository, conceptMapper,
                mock(PersonRepository.class), mock(PersonMapper.class), mock(ActionUnitRepository.class),
                mock(ActionUnitSummaryMapper.class),                 mock(SpatialUnitRepository.class), mock(SpatialUnitSummaryMapper.class),
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

        var arg2 = form(phases);
        var arg3 = Map.of("8", new AnswerInput(null, List.of(30)));
        assertThatThrownBy(() -> service.apply(entity, arg2, arg3, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void apply_rejectsAFieldAbsentFromTheForm_whileApplyLenientSkipsIt() {
        CustomFieldText known = text(1L, false);
        givenResponse(Map.of(known, new CustomFieldAnswerTextViewModel()));
        Map<String, AnswerInput> answers = Map.of("1", new AnswerInput("ok", null), "404", new AnswerInput("x", null));

        var arg2 = form(known);
        assertThatThrownBy(() -> service.apply(entity, arg2, answers, PROJECT_ID))
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

        var arg2 = form(decimal);
        assertThatThrownBy(() -> service.apply(entity, arg2, answers, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class);

        service.applyLenient(entity, form(decimal), answers, PROJECT_ID);
        verify(formService, never()).applyTypedValueToAnswer(any(), any());
    }

    // A readOnly column (the project a recording unit belongs to, a generated identifier) is never
    // written: moving an entity to another project is not an edit.
    @Test
    void apply_rejectsAFieldTheFormMarksReadOnly_whileApplyLenientSkipsIt() {
        CustomFieldText project = text(305L, true);
        CustomFieldText title = text(1L, false);
        givenResponse(Map.of(project, new CustomFieldAnswerTextViewModel(), title, new CustomFieldAnswerTextViewModel()));
        FormUiDto form = form(project, title);
        form.getLayout().get(0).getRows().get(0).getColumns().get(0).setReadOnly(true);
        Map<String, AnswerInput> answers = Map.of("305", new AnswerInput("x", null), "1", new AnswerInput("ok", null));

        assertThatThrownBy(() -> service.apply(entity, form, answers, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Champ non modifiable");

        Map<CustomField, CustomFieldAnswerViewModel> lenient = service.applyLenient(entity, form, answers, PROJECT_ID);
        assertThat(lenient).containsOnlyKeys(title);
    }

    @Test
    void isReadOnlyIn_readsTheRealDetailsForms() {
        CustomField project = fr.siamois.ui.table.definitions.SystemFieldCatalog.fieldBoundTo(
                fr.siamois.domain.models.settings.tableconfig.ConfigurableTable.UE, "actionUnit");

        assertThat(FieldAnswerPatchService.isReadOnlyIn(fr.siamois.utils.TestForms.of(fr.siamois.domain.models.settings.tableconfig.ConfigurableTable.UE), project)).isTrue();
        assertThat(FieldAnswerPatchService.isReadOnlyIn(form(project), project)).isFalse();
    }

    // ========== add / remove ==========

    // The client may have read only a preview of the list: add/remove is applied to what the entity
    // holds, so the values it never saw are kept.
    @Test
    void apply_addRemove_changesOnlyTheNamedValues_ofWhatTheEntityHolds() {
        CustomFieldSelectMultiplePhase phases = new CustomFieldSelectMultiplePhase();
        phases.setId(8L);
        CustomFieldAnswerSelectMultiplePhaseViewModel vm = new CustomFieldAnswerSelectMultiplePhaseViewModel();
        givenResponse(Map.of(phases, vm));
        when(formService.readAnswerValueForApi(vm)).thenReturn(new LinkedHashSet<>(List.of(phase(1L), phase(2L), phase(3L))));
        Phase added = new Phase();
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT_ID);
        added.setActionUnit(project);
        when(phaseRepository.findById(4L)).thenReturn(Optional.of(added));
        when(phaseMapper.convert(added)).thenReturn(phase(4L));

        service.apply(entity, form(phases), Map.of("8", new AnswerInput(null, null, List.of(4, 1), List.of("2"))), PROJECT_ID);

        verify(formService).applyTypedValueToAnswer(same(vm), argThat(value -> value instanceof Set<?> set
                && set.stream().map(p -> ((PhaseDTO) p).getId()).toList().equals(List.of(1L, 3L, 4L))));
    }

    @Test
    void apply_addRemove_readsARawJsonMapToo() {
        CustomFieldSelectMultiplePhase phases = new CustomFieldSelectMultiplePhase();
        phases.setId(8L);
        CustomFieldAnswerSelectMultiplePhaseViewModel vm = new CustomFieldAnswerSelectMultiplePhaseViewModel();
        givenResponse(Map.of(phases, vm));
        when(formService.readAnswerValueForApi(vm)).thenReturn(new LinkedHashSet<>(List.of(phase(1L), phase(2L))));

        service.apply(entity, form(phases), Map.of("8", Map.of("remove", List.of(1))), PROJECT_ID);

        verify(formService).applyTypedValueToAnswer(same(vm), argThat(value -> value instanceof Set<?> set
                && set.stream().map(p -> ((PhaseDTO) p).getId()).toList().equals(List.of(2L))));
    }

    @Test
    void apply_rejectsValuesTogetherWithAddRemove_andAddRemoveOnAScalarField() {
        CustomFieldSelectMultiplePhase phases = new CustomFieldSelectMultiplePhase();
        phases.setId(8L);
        CustomFieldText title = text(1L, false);
        givenResponse(Map.of(phases, new CustomFieldAnswerSelectMultiplePhaseViewModel(), title, new CustomFieldAnswerTextViewModel()));

        var arg2 = form(phases, title);
        var arg3 = Map.of("8", new AnswerInput(null, List.of(1), List.of(2), null));
        assertThatThrownBy(() -> service.apply(entity, arg2, arg3, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ne se combinent pas");
        var scalarForm = form(phases, title);
        var addOnScalar = Map.of("1", new AnswerInput(null, null, List.of("x"), null));
        assertThatThrownBy(() -> service.apply(entity, scalarForm, addOnScalar, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("multivalué");
        verify(formService, never()).applyTypedValueToAnswer(any(), any());
    }

    // ========== Form rules (FieldRules) checked at PATCH ==========

    @Test
    void apply_refusesAValueInAFieldTheRulesDisable_butAcceptsClearingIt() {
        CustomFieldText nature = text(1L, false);
        CustomFieldText erosion = text(2L, false);
        CustomFieldAnswerTextViewModel natureVm = new CustomFieldAnswerTextViewModel();
        givenResponse(Map.of(nature, natureVm, erosion, new CustomFieldAnswerTextViewModel()));
        lenient().when(formService.readAnswerValueForApi(natureVm)).thenReturn("fosse");
        FormUiDto form = formOf(col(nature), col(erosion, FieldRules.NONE.withEnabledWhen(
                Condition.eq(1L, FieldValueSpec.literal("érosion")))));

        var arg3 = Map.of("2", new AnswerInput("en V", null));
        assertThatThrownBy(() -> service.apply(entity, form, arg3, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("inactif");

        Map<String, AnswerInput> clear = new HashMap<>();
        clear.put("2", new AnswerInput(null, null));
        service.apply(entity, form, clear, PROJECT_ID);
        verify(formService).applyTypedValueToAnswer(any(), isNull());
    }

    @Test
    void apply_refusesClearingARequiredField() {
        CustomFieldText title = text(1L, false);
        givenResponse(Map.of(title, new CustomFieldAnswerTextViewModel()));
        CustomColUiDto required = col(title);
        required.setRequired(true);

        Map<String, AnswerInput> clear = new HashMap<>();
        clear.put("1", new AnswerInput(null, null));
        var arg2 = formOf(required);
        assertThatThrownBy(() -> service.apply(entity, arg2, clear, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("obligatoire");
    }

    @Test
    void apply_refusesBreakingAnOrderingConstraint_onEitherSide() {
        CustomFieldDecimal zInf = new CustomFieldDecimal();
        zInf.setId(3L);
        CustomFieldDecimal zSup = new CustomFieldDecimal();
        zSup.setId(4L);
        CustomFieldAnswerDecimalViewModel zSupVm = new CustomFieldAnswerDecimalViewModel();
        givenResponse(Map.of(zInf, new CustomFieldAnswerDecimalViewModel(), zSup, zSupVm));
        lenient().when(formService.readAnswerValueForApi(zSupVm)).thenReturn(10.0);
        FormUiDto form = formOf(col(zInf), col(zSup, FieldRules.NONE.withConstraints(FieldConstraint.gte(3L))));

        // Z inf written above the stored Z sup: the constraint is declared on Z sup, checked from Z inf too.
        var arg3 = Map.of("3", new AnswerInput(12.0, null));
        assertThatThrownBy(() -> service.apply(entity, form, arg3, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("incompatible");

        service.apply(entity, form, Map.of("3", new AnswerInput(8.0, null)), PROJECT_ID);
    }

    @Test
    void apply_neverBlocksOnAnIncoherenceOfAnotherField() {
        CustomFieldText nature = text(1L, false);
        CustomFieldText erosion = text(2L, false);
        CustomFieldText note = text(5L, false);
        CustomFieldAnswerTextViewModel erosionVm = new CustomFieldAnswerTextViewModel();
        givenResponse(Map.of(nature, new CustomFieldAnswerTextViewModel(), erosion, erosionVm, note, new CustomFieldAnswerTextViewModel()));
        // erosion holds a value although the nature doesn't enable it: kept, flagged by the client.
        lenient().when(formService.readAnswerValueForApi(erosionVm)).thenReturn("en V");
        FormUiDto form = formOf(col(nature), col(erosion, FieldRules.NONE.withEnabledWhen(
                Condition.eq(1L, FieldValueSpec.literal("érosion")))), col(note));

        service.apply(entity, form, Map.of("1", new AnswerInput("fosse", null), "5", new AnswerInput("ok", null)), PROJECT_ID);

        verify(formService).updateJpaEntityFromResponse(any(), same(entity));
    }

    @Test
    void apply_refusesAConceptOutsideTheListRelatedToTheParentAnswer() {
        CustomFieldSelectOneFromFieldCode nature = new CustomFieldSelectOneFromFieldCode();
        nature.setId(1L);
        CustomFieldSelectOneFromFieldCode interpretation = new CustomFieldSelectOneFromFieldCode();
        interpretation.setId(2L);
        CustomFieldAnswerSelectOneFromFieldCodeViewModel natureVm = new CustomFieldAnswerSelectOneFromFieldCodeViewModel();
        givenResponse(Map.of(nature, natureVm, interpretation, new CustomFieldAnswerSelectOneFromFieldCodeViewModel()));
        ConceptDTO natureValue = new ConceptDTO();
        natureValue.setId(77L);
        lenient().when(formService.readAnswerValueForApi(natureVm)).thenReturn(natureValue);
        fr.siamois.domain.models.vocabulary.Concept related = new fr.siamois.domain.models.vocabulary.Concept();
        related.setId(80L);
        related.setExternalId("80");
        fr.siamois.domain.models.vocabulary.Concept unrelated = new fr.siamois.domain.models.vocabulary.Concept();
        unrelated.setId(81L);
        unrelated.setExternalId("81");
        when(conceptRepository.findById(80L)).thenReturn(Optional.of(related));
        when(conceptRepository.findById(81L)).thenReturn(Optional.of(unrelated));
        ConceptDTO relatedDto = new ConceptDTO();
        relatedDto.setId(80L);
        ConceptDTO unrelatedDto = new ConceptDTO();
        unrelatedDto.setId(81L);
        when(conceptMapper.convert(related)).thenReturn(relatedDto);
        when(conceptMapper.convert(unrelated)).thenReturn(unrelatedDto);
        when(conceptRepository.isRelated(77L, 80L)).thenReturn(true);
        when(conceptRepository.isRelated(77L, 81L)).thenReturn(false);
        FormUiDto form = formOf(col(nature), col(interpretation, FieldRules.NONE.withOptions(new OptionsFilter.RelatedConcepts(1L))));

        service.apply(entity, form, Map.of("2", new AnswerInput("80", null)), PROJECT_ID);
        var arg3 = Map.of("2", new AnswerInput("81", null));
        assertThatThrownBy(() -> service.apply(entity, form, arg3, PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("hors de la liste");
    }

    @Test
    void applyLenient_keepsAValueBreakingTheRules() {
        CustomFieldText title = text(1L, false);
        givenResponse(Map.of(title, new CustomFieldAnswerTextViewModel()));
        CustomColUiDto required = col(title);
        required.setRequired(true);
        Map<String, AnswerInput> clear = new HashMap<>();
        clear.put("1", new AnswerInput(null, null));

        service.applyLenient(entity, formOf(required), clear, PROJECT_ID);

        verify(formService).applyTypedValueToAnswer(any(), isNull());
    }

    private static CustomColUiDto col(CustomField field) {
        return col(field, FieldRules.NONE);
    }

    private static CustomColUiDto col(CustomField field, FieldRules rules) {
        CustomColUiDto col = new CustomColUiDto();
        col.setField(field);
        col.setRules(rules);
        return col;
    }

    private static FormUiDto formOf(CustomColUiDto... columns) {
        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(new ArrayList<>(List.of(columns)));
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));
        FormUiDto form = new FormUiDto();
        form.setLayout(List.of(panel));
        return form;
    }

    private static PhaseDTO phase(long id) {
        PhaseDTO phase = new PhaseDTO();
        phase.setId(id);
        return phase;
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
