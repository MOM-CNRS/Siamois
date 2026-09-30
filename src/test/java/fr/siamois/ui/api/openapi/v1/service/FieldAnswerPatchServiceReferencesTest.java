package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionCode;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.container.CustomFieldSelectMultipleContainer;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectOneRecordingUnit;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.specimen.CustomFieldSelectMultipleSpecimen;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultiple;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
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
import fr.siamois.mapper.ActionCodeMapper;
import fr.siamois.mapper.ActionUnitSummaryMapper;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.ContainerMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.PhaseMapper;
import fr.siamois.mapper.RecordingUnitSummaryMapper;
import fr.siamois.mapper.SpatialUnitSummaryMapper;
import fr.siamois.mapper.SpecimenSummaryMapper;
import fr.siamois.mapper.UnitDefinitionMapper;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How a raw JSON reference (an id, an object, a list of them) becomes what the form's view models take. */
@SuppressWarnings({"unchecked", "rawtypes"})
class FieldAnswerPatchServiceReferencesTest {

    private static final long PROJECT = 7L;

    private final FormService formService = mock(FormService.class);
    private final PersonRepository persons = mock(PersonRepository.class);
    private final ActionUnitRepository actionUnits = mock(ActionUnitRepository.class);
    private final ActionCodeRepository actionCodes = mock(ActionCodeRepository.class);
    private final SpatialUnitRepository spatialUnits = mock(SpatialUnitRepository.class);
    private final RecordingUnitRepository recordingUnits = mock(RecordingUnitRepository.class);
    private final ContainerRepository containers = mock(ContainerRepository.class);
    private final SpecimenRepository specimens = mock(SpecimenRepository.class);
    private final ConceptRepository concepts = mock(ConceptRepository.class);
    private final PersonMapper personMapper = mock(PersonMapper.class);
    private final PhaseDTO entity = new PhaseDTO();
    private FieldAnswerPatchService service;

    /** A mapper whose every conversion gives a mock of its declared return type. */
    private static <T> T mapper(Class<T> type) {
        return mock(type, org.mockito.Mockito.RETURNS_MOCKS);
    }

    @BeforeEach
    void setUp() {
        service = new FieldAnswerPatchService(formService, concepts, mapper(ConceptMapper.class),
                persons, personMapper, actionUnits, mapper(ActionUnitSummaryMapper.class),
                actionCodes, mapper(ActionCodeMapper.class), spatialUnits, mapper(SpatialUnitSummaryMapper.class),
                recordingUnits, mapper(RecordingUnitSummaryMapper.class), mock(PhaseRepository.class), mapper(PhaseMapper.class),
                containers, mapper(ContainerMapper.class), specimens, mapper(SpecimenSummaryMapper.class), mock(UnitDefinitionMapper.class));
    }

    // ---------- helpers ----------

    private <T extends CustomField> T field(T field) {
        field.setId(1L);
        field.setIsSystemField(false);
        return field;
    }

    private static FormUiDto form(CustomField field) {
        CustomColUiDto col = new CustomColUiDto();
        col.setField(field);
        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(new ArrayList<>(List.of(col)));
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));
        FormUiDto form = new FormUiDto();
        form.setLayout(List.of(panel));
        return form;
    }

    private Object applied(CustomField field, AnswerInput input) {
        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(field, mock(CustomFieldAnswerViewModel.class));
        response.setAnswers(answers);
        when(formService.initOrReuseResponse(isNull(), same(entity), any(), eq(true))).thenReturn(response);

        service.apply(entity, form(field), Map.of("1", input), PROJECT);

        ArgumentCaptor<Object> typed = ArgumentCaptor.forClass(Object.class);
        verify(formService, org.mockito.Mockito.atLeastOnce()).applyTypedValueToAnswer(any(), typed.capture());
        return typed.getValue();
    }

    private HttpStatus refused(CustomField field, AnswerInput input) {
        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(field, mock(CustomFieldAnswerViewModel.class));
        response.setAnswers(answers);
        when(formService.initOrReuseResponse(isNull(), same(entity), any(), eq(true))).thenReturn(response);
        ResponseStatusException e = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> service.apply(entity, form(field), Map.of("1", input), PROJECT));
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    private static ActionUnit project(long id) {
        ActionUnit au = new ActionUnit();
        au.setId(id);
        return au;
    }

    // ---------- concepts, people, projects, codes, places ----------

    @Test
    void conceptsAreReadByIdFromAScalarOrAnObject_andAMissingOneIs400() {
        when(concepts.findById(5L)).thenReturn(Optional.of(new Concept()));
        when(concepts.findById(6L)).thenReturn(Optional.of(new Concept()));

        assertThat(applied(field(new CustomFieldSelectOne()), new AnswerInput(5, null))).isNotNull();
        assertThat((java.util.Collection<?>) applied(field(new CustomFieldSelectMultiple()), new AnswerInput(null, List.of(5, Map.of("id", 6))))).isNotEmpty();

        when(concepts.findById(404L)).thenReturn(Optional.empty());
        assertThat(refused(field(new CustomFieldSelectOne()), new AnswerInput(404, null))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(field(new CustomFieldSelectOne()), new AnswerInput("pas un id", null))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void peopleProjectsCodesAndPlaces() {
        when(personMapper.convert(any(Person.class))).thenReturn(new fr.siamois.dto.entity.PersonDTO());
        when(persons.findById(anyLong())).thenReturn(Optional.of(new Person()));
        when(actionUnits.findById(anyLong())).thenReturn(Optional.of(project(PROJECT)));
        when(actionCodes.findById("OA-1")).thenReturn(Optional.of(new ActionCode()));
        when(actionCodes.findById("KO")).thenReturn(Optional.empty());
        when(spatialUnits.findById(anyLong())).thenReturn(Optional.of(new SpatialUnit()));

        assertThat(applied(field(new CustomFieldSelectOneActionUnit()), new AnswerInput(9, null))).isNotNull();
        assertThat(applied(field(CustomFieldSelectOneActionCode.builder().build()), new AnswerInput("OA-1", null))).isNotNull();
        assertThat(applied(field(CustomFieldSelectOneActionCode.builder().build()), new AnswerInput(Map.of("id", "OA-1"), null))).isNotNull();
        assertThat(applied(field(new CustomFieldSelectOneSpatialUnit()), new AnswerInput(4, null))).isNotNull();
        assertThat((java.util.Collection<?>) applied(field(new CustomFieldSelectMultipleSpatialUnitTree()), new AnswerInput(null, List.of(4)))).isNotEmpty();

        assertThat(refused(field(CustomFieldSelectOneActionCode.builder().build()), new AnswerInput("KO", null))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- entities of the project ----------

    @Test
    void recordingUnitsContainersAndFindsMustBelongToTheProject() {
        RecordingUnit here = new RecordingUnit();
        here.setActionUnit(project(PROJECT));
        RecordingUnit elsewhere = new RecordingUnit();
        elsewhere.setActionUnit(project(99L));
        when(recordingUnits.findById(1L)).thenReturn(Optional.of(here));
        when(recordingUnits.findById(2L)).thenReturn(Optional.of(elsewhere));
        Container container = new Container();
        container.setActionUnit(project(PROJECT));
        when(containers.findById(3L)).thenReturn(Optional.of(container));
        Specimen specimen = new Specimen();
        specimen.setActionUnit(project(PROJECT));
        when(specimens.findById(4L)).thenReturn(Optional.of(specimen));

        assertThat(applied(field(new CustomFieldSelectOneRecordingUnit()), new AnswerInput(1, null))).isNotNull();
        assertThat((java.util.Collection<?>) applied(field(new CustomFieldSelectMultipleRecordingUnit()), new AnswerInput(null, List.of(1)))).isNotEmpty();
        assertThat((java.util.Collection<?>) applied(field(new CustomFieldSelectMultipleContainer()), new AnswerInput(null, List.of(3)))).isNotEmpty();
        assertThat((java.util.Collection<?>) applied(field(new CustomFieldSelectMultipleSpecimen()), new AnswerInput(null, List.of(4)))).isNotEmpty();

        assertThat(refused(field(new CustomFieldSelectOneRecordingUnit()), new AnswerInput(2, null))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(field(new CustomFieldSelectMultipleRecordingUnit()), new AnswerInput(null, List.of(2)))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- scalars ----------

    @Test
    void anIntegerIsReadFromANumberOrAString() {
        assertThat(applied(field(new CustomFieldInteger()), new AnswerInput(" 12 ", null))).isEqualTo(12);
        assertThat(applied(field(new CustomFieldInteger()), new AnswerInput(7.0, null))).isEqualTo(7);
    }

    @Test
    void aMeasurementIsANumberOrAnObject_andAnythingElseIs400() {
        assertThat(refused(field(new CustomFieldMeasurement()), new AnswerInput("beaucoup", null))).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aFieldTypeTheApiDoesNotWriteIs400() {
        assertThat(refused(field(new fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneAddress()),
                new AnswerInput("x", null))).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
