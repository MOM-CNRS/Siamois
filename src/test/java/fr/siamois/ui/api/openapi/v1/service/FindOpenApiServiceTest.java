package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.find.FindCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.find.FindPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FindOpenApiServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L);
    private static final String LANG = "fr";

    @Mock
    private SpecimenService specimenService;
    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private FormService formService;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private ConversionService conversionService;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private PersonService personService;
    @Mock
    private PersonMapper personMapper;
    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private FindOpenApiMapper findOpenApiMapper;

    private FindOpenApiService service;

    private PersonDTO personDto;
    private InstitutionDTO institution;
    private RecordingUnitDTO recordingUnit;
    private ConceptDTO typeDto;
    private FindResource findResource;

    @BeforeEach
    void setUp() {
        service = new FindOpenApiService(
                specimenService,
                recordingUnitService,
                formService,
                conceptRepository,
                conceptMapper,
                conversionService,
                profilePermissionService,
                personService,
                personMapper,
                actionUnitService,
                spatialUnitService,
                findOpenApiMapper);

        personDto = new PersonDTO();
        personDto.setId(1L);

        institution = new InstitutionDTO();
        institution.setId(10L);

        recordingUnit = new RecordingUnitDTO();
        recordingUnit.setId(42L);
        recordingUnit.setCreatedByInstitution(institution);

        typeDto = new ConceptDTO();
        typeDto.setId(3L);

        findResource = new FindResource();
        findResource.setResourceType("finds");
        findResource.setId("99");
        lenient().when(findOpenApiMapper.toResource(any(SpecimenDTO.class))).thenReturn(findResource);
        lenient().when(findOpenApiMapper.toResource(isNull())).thenReturn(findResource);
        lenient().when(profilePermissionService.hasRecordingUnitWritePermission(any(), any(RecordingUnitDTO.class))).thenReturn(true);
    }

    // --- createFind ---

    @Test
    void createFind_blankRecordingUnitId_throws400() {
        FindCreateRequest request = new FindCreateRequest();
        request.setRecordingUnitId("  ");
        request.setTypeId("3");

        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createFind_blankSpecimenTypeConceptId_throws400() {
        FindCreateRequest request = createRequest("UE-1", null);

        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createFind_recordingUnitWithoutInstitution_throws400() {
        recordingUnit.setCreatedByInstitution(null);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        var request = createRequest("UE-1", "3");
        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("UE sans organisation");
    }

    @Test
    void createFind_recordingUnitWithInstitutionWithoutId_throws400() {
        InstitutionDTO instWithoutId = new InstitutionDTO();
        recordingUnit.setCreatedByInstitution(instWithoutId);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        var request = createRequest("UE-1", "3");
        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("UE sans organisation");
    }

    @Test
    void createFind_withoutWritePermission_throws403() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();
        when(profilePermissionService.hasRecordingUnitWritePermission(any(), any(RecordingUnitDTO.class))).thenReturn(false);

        var request = createRequest("UE-1", "3");
        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createFind_typeConceptNotFound_throws404() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        when(conceptRepository.findById(3L)).thenReturn(Optional.empty());

        var request = createRequest("UE-1", "3");
        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createFind_withoutCustomForm_savesShell() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(null);

        SpecimenDTO saved = new SpecimenDTO();
        saved.setId(99L);
        when(specimenService.save(any(SpecimenDTO.class))).thenReturn(saved);

        FindResource result = service.createFind(createRequest("UE-1", "3"), personDto, SCOPE, LANG);

        assertThat(result).isSameAs(findResource);
        verify(specimenService).save(any(SpecimenDTO.class));
        verifyNoInteractions(conversionService);
    }

    @Test
    void createFind_withoutCustomForm_withFieldAnswers_throws400() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(null);

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("1", new AnswerInput("x", null)));

        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(specimenService, never()).save(any());
    }

    @Test
    void createFind_withCustomForm_appliesFieldAnswersAndSaves() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldInteger intField = integerField(1L);
        CustomFieldText textField = textField(2L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(intField, textField);
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(intField, new CustomFieldAnswerIntegerViewModel());
        answers.put(textField, new CustomFieldAnswerTextViewModel());
        responseVm.setAnswers(answers);

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);

        SpecimenDTO saved = new SpecimenDTO();
        saved.setId(99L);
        when(specimenService.save(any(SpecimenDTO.class))).thenReturn(saved);

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of(
                "1", new AnswerInput(7, null),
                "2", new AnswerInput("note", null),
                "not-a-number", new AnswerInput("oops", null),
                "999", new AnswerInput(1, null)));

        FindResource result = service.createFind(request, personDto, SCOPE, LANG);

        assertThat(result).isSameAs(findResource);
        verify(formService).applyTypedValueToAnswer(any(), eq(7));
        verify(formService).applyTypedValueToAnswer(any(), eq("note"));
        verify(formService).updateJpaEntityFromResponse(same(responseVm), any(SpecimenDTO.class));
        verify(specimenService).save(any(SpecimenDTO.class));
    }

    @Test
    void createFind_withCustomForm_nullFieldAnswers_usesEmptyMap() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = new FormUiDto();
        formUi.setLayout(List.of());
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(new HashMap<>());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(specimenService.save(any(SpecimenDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(null);

        service.createFind(request, personDto, SCOPE, LANG);

        verify(formService).updateJpaEntityFromResponse(same(responseVm), any(SpecimenDTO.class));
    }

    @Test
    void createFind_mergesAnswersWhenViewModelUsesDifferentFieldInstanceWithSameId() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldInteger fieldLayout = integerField(5L);
        CustomFieldInteger fieldDb = integerField(5L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(fieldLayout);

        CustomFieldAnswerIntegerViewModel answerVm = new CustomFieldAnswerIntegerViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(new HashMap<>(Map.of(fieldDb, answerVm)));

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(specimenService.save(any(SpecimenDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("5", new AnswerInput(42, null)));

        service.createFind(request, personDto, SCOPE, LANG);

        verify(formService).applyTypedValueToAnswer(same(answerVm), eq(42));
    }

    @Test
    void createFind_coercesAllSupportedFieldTypes() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldInteger intField = integerField(1L);
        CustomFieldText textField = textField(2L);
        CustomFieldDateTime dtField = dateTimeField(3L);
        CustomFieldSelectOneFromFieldCode conceptField = conceptField(4L);
        CustomFieldSelectOnePerson personField = personField(5L);
        CustomFieldSelectOneActionUnit projectField = projectField(6L);
        CustomFieldSelectOneSpatialUnit suField = spatialField(7L);
        CustomFieldSelectOne unsupported = unsupportedField(8L);

        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(
                intField, textField, dtField, conceptField, personField, projectField, suField, unsupported);

        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(intField, new CustomFieldAnswerIntegerViewModel());
        answers.put(textField, new CustomFieldAnswerTextViewModel());
        answers.put(dtField, new fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerDateTimeViewModel());
        answers.put(conceptField, new CustomFieldAnswerSelectOneFromFieldCodeViewModel());
        answers.put(personField, new CustomFieldAnswerSelectOnePersonViewModel());
        answers.put(projectField, new CustomFieldAnswerSelectOneActionUnitViewModel());
        answers.put(suField, new CustomFieldAnswerSelectOneSpatialUnitViewModel());
        answers.put(unsupported, new CustomFieldAnswerTextViewModel());
        responseVm.setAnswers(answers);

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(specimenService.save(any(SpecimenDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        Concept conceptEntity = new Concept();
        conceptEntity.setId(100L);
        when(conceptRepository.findById(100L)).thenReturn(Optional.of(conceptEntity));
        ConceptDTO conceptDto = new ConceptDTO();
        conceptDto.setId(100L);
        when(conceptMapper.convert(conceptEntity)).thenReturn(conceptDto);

        Person personEntity = new Person();
        personEntity.setId(8L);
        when(personService.findById(8L)).thenReturn(personEntity);
        PersonDTO personAnswer = new PersonDTO();
        personAnswer.setId(8L);
        when(personMapper.convert(personEntity)).thenReturn(personAnswer);

        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(20L);
        au.setName("Projet");
        au.setIdentifier("P1");
        au.setFullIdentifier("ORG-P1");
        au.setRecordingUnitIdentifierFormat("%s");
        au.setType(typeDto);
        au.setBeginDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
        au.setEndDate(OffsetDateTime.parse("2024-12-31T00:00:00Z"));
        au.setMinRecordingUnitCode(1);
        au.setMaxRecordingUnitCode(99);
        when(actionUnitService.findById(20L)).thenReturn(au);

        SpatialUnitDTO su = new SpatialUnitDTO();
        su.setId(30L);
        su.setName("Zone A");
        when(spatialUnitService.findById(30L)).thenReturn(su);

        OffsetDateTime odt = OffsetDateTime.parse("2025-06-01T10:15:30Z");
        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of(
                "1", new AnswerInput("15", null),
                "2", new AnswerInput(123, null),
                "3", new AnswerInput(odt, null),
                "4", new AnswerInput(Map.of("id", "100"), null),
                "5", new AnswerInput("8", null),
                "6", new AnswerInput(20L, null),
                "7", new AnswerInput(Map.of("id", 30), null),
                "8", new AnswerInput("ignored", null)
        ));

        service.createFind(request, personDto, SCOPE, LANG);

        verify(formService).applyTypedValueToAnswer(any(), eq(15));
        verify(formService).applyTypedValueToAnswer(any(), eq("123"));
        verify(formService).applyTypedValueToAnswer(any(), eq(odt));
        verify(formService).applyTypedValueToAnswer(any(), eq(conceptDto));
        verify(formService).applyTypedValueToAnswer(any(), eq(personAnswer));
        verify(formService).applyTypedValueToAnswer(
                any(CustomFieldAnswerSelectOneActionUnitViewModel.class),
                org.mockito.ArgumentMatchers.argThat(v ->
                        v instanceof ActionUnitSummaryDTO s && Long.valueOf(20L).equals(s.getId())));
    }

    @Test
    void createFind_invalidIntegerValue_skipsField() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldInteger intField = integerField(1L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(intField);
        CustomFormResponseViewModel responseVm = responseWith(intField, new CustomFieldAnswerIntegerViewModel());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(specimenService.save(any(SpecimenDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("1", new AnswerInput("not-a-number", null)));

        service.createFind(request, personDto, SCOPE, LANG);

        verify(formService, never()).applyTypedValueToAnswer(any(), any());
    }

    @Test
    void createFind_fieldWithoutViewModel_skipsApply() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldInteger withVm = integerField(1L);
        CustomFieldInteger withoutVm = integerField(2L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(withVm, withoutVm);
        CustomFormResponseViewModel responseVm = responseWith(withVm, new CustomFieldAnswerIntegerViewModel());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(specimenService.save(any(SpecimenDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("1", new AnswerInput(1, null), "2", new AnswerInput(2, null)));

        service.createFind(request, personDto, SCOPE, LANG);

        verify(formService, times(1)).applyTypedValueToAnswer(any(), eq(1));
    }

    @Test
    void createFind_datetimeStringAndNullIntegerValue() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldDateTime dtField = dateTimeField(10L);
        CustomFieldInteger intField = integerField(11L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(dtField, intField);

        CustomFieldAnswerIntegerViewModel intVm = new CustomFieldAnswerIntegerViewModel();
        CustomFormResponseViewModel responseVm = responseWith(dtField,
                new fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerDateTimeViewModel());
        responseVm.getAnswers().put(intField, intVm);

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(specimenService.save(any(SpecimenDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        FindCreateRequest request = createRequest("UE-1", "3");
        Map<String, AnswerInput> fieldAnswers = new HashMap<>();
        fieldAnswers.put("10", new AnswerInput("2025-01-02T08:00:00Z", null));
        fieldAnswers.put("11", new AnswerInput(null, null));
        request.setFieldAnswers(fieldAnswers);

        service.createFind(request, personDto, SCOPE, LANG);

        verify(formService).applyTypedValueToAnswer(any(), eq(OffsetDateTime.parse("2025-01-02T08:00:00Z")));
        verify(formService).applyTypedValueToAnswer(same(intVm), isNull());
    }

    @Test
    void createFind_invalidDatetimeFormat_skipsValue() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldDateTime dtField = dateTimeField(10L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(dtField);
        CustomFormResponseViewModel responseVm = responseWith(dtField,
                new fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerDateTimeViewModel());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(specimenService.save(any(SpecimenDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("10", new AnswerInput("not-a-date", null)));

        service.createFind(request, personDto, SCOPE, LANG);

        verify(formService, never()).applyTypedValueToAnswer(any(), any());
    }

    @Test
    void createFind_conceptNotFound_throws400() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldSelectOneFromFieldCode conceptField = conceptField(4L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(conceptField);
        CustomFormResponseViewModel responseVm = responseWith(conceptField,
                new CustomFieldAnswerSelectOneFromFieldCodeViewModel());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(conceptRepository.findById(404L)).thenReturn(Optional.empty());

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("4", new AnswerInput(404L, null)));

        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createFind_personNotFound_throws400() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldSelectOnePerson personField = personField(5L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(personField);
        CustomFormResponseViewModel responseVm = responseWith(personField, new CustomFieldAnswerSelectOnePersonViewModel());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(personService.findById(99L)).thenReturn(null);

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("5", new AnswerInput(99L, null)));

        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("Personne introuvable");
    }

    @Test
    void createFind_projectNotFound_throws400() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldSelectOneActionUnit projectField = projectField(6L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(projectField);
        CustomFormResponseViewModel responseVm = responseWith(projectField, new CustomFieldAnswerSelectOneActionUnitViewModel());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(actionUnitService.findById(77L)).thenReturn(null);

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("6", new AnswerInput(77L, null)));

        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("Projet introuvable");
    }

    @Test
    void createFind_spatialUnitNotFound_throws400() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldSelectOneSpatialUnit suField = spatialField(7L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(suField);
        CustomFormResponseViewModel responseVm = responseWith(suField, new CustomFieldAnswerSelectOneSpatialUnitViewModel());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(spatialUnitService.findById(55L)).thenThrow(new RuntimeException("missing"));

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("7", new AnswerInput(55L, null)));

        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("Unité spatiale introuvable");
    }

    @Test
    void createFind_invalidConceptIdFormat_throws400() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomFieldSelectOneFromFieldCode conceptField = conceptField(4L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(conceptField);
        CustomFormResponseViewModel responseVm = responseWith(conceptField,
                new CustomFieldAnswerSelectOneFromFieldCodeViewModel());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("4", new AnswerInput(Map.of(), null)));

        assertThatThrownBy(() -> service.createFind(request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("concept");
    }

    @Test
    void createFind_nullAnswersOnResponse_skipsMerge() {
        when(recordingUnitService.findAccessibleRecordingUnitByKey("UE-1", SCOPE, null)).thenReturn(recordingUnit);
        stubTypeConcept();

        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(integerField(1L));
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(null);

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), any(SpecimenDTO.class), any(), eq(true))).thenReturn(responseVm);
        when(specimenService.save(any(SpecimenDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        FindCreateRequest request = createRequest("UE-1", "3");
        request.setFieldAnswers(Map.of("1", new AnswerInput(1, null)));

        service.createFind(request, personDto, SCOPE, LANG);

        verify(formService, never()).applyTypedValueToAnswer(any(), any());
    }

    // --- patchFind ---

    @Test
    void patchFind_notFound_throws404() {
        when(specimenService.findAccessibleById(99L, SCOPE)).thenReturn(Optional.empty());

        var patchRequest = new FindPatchRequest();
        assertThatThrownBy(() -> service.patchFind(99L, patchRequest, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void patchFind_withoutInstitution_throws400() {
        SpecimenDTO specimen = accessibleSpecimen();
        specimen.setCreatedByInstitution(null);
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));

        var patchRequest = new FindPatchRequest();
        assertThatThrownBy(() -> service.patchFind(7L, patchRequest, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Mobilier sans organisation");
    }

    @Test
    void patchFind_institutionWithoutId_throws400() {
        SpecimenDTO specimen = accessibleSpecimen();
        specimen.setCreatedByInstitution(new InstitutionDTO());
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));

        var patchRequest = new FindPatchRequest();
        assertThatThrownBy(() -> service.patchFind(7L, patchRequest, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Mobilier sans organisation");
    }

    @Test
    void patchFind_withoutRecordingUnit_throws400() {
        SpecimenDTO specimen = accessibleSpecimen();
        specimen.setRecordingUnit(null);
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));

        var patchRequest = new FindPatchRequest();
        assertThatThrownBy(() -> service.patchFind(7L, patchRequest, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Mobilier sans unité d'enregistrement");
    }

    @Test
    void patchFind_withoutWritePermission_throws403() {
        SpecimenDTO specimen = accessibleSpecimen();
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(), any(RecordingUnitDTO.class))).thenReturn(false);

        var patchRequest = new FindPatchRequest();
        assertThatThrownBy(() -> service.patchFind(7L, patchRequest, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void patchFind_emptyFieldAnswers_returnsWithoutSave() {
        SpecimenDTO specimen = accessibleSpecimen();
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);

        FindPatchRequest request = new FindPatchRequest();
        request.setFieldAnswers(Map.of());

        FindResource result = service.patchFind(7L, request, personDto, SCOPE, LANG);

        assertThat(result).isSameAs(findResource);
        verify(specimenService, never()).save(any());
        verify(findOpenApiMapper).toResource(same(specimen));
    }

    @Test
    void patchFind_withoutSpecimenType_throws400() {
        SpecimenDTO specimen = accessibleSpecimen();
        specimen.setType(null);
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);

        FindPatchRequest request = new FindPatchRequest();
        request.setFieldAnswers(Map.of("1", new AnswerInput("x", null)));

        assertThatThrownBy(() -> service.patchFind(7L, request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Mobilier sans type");
    }

    @Test
    void patchFind_withoutCustomForm_throws400() {
        SpecimenDTO specimen = accessibleSpecimen();
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(null);

        FindPatchRequest request = new FindPatchRequest();
        request.setFieldAnswers(Map.of("1", new AnswerInput("x", null)));

        assertThatThrownBy(() -> service.patchFind(7L, request, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void patchFind_withFieldAnswers_updatesAndSaves() {
        SpecimenDTO specimen = accessibleSpecimen();
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);

        CustomFieldText textField = textField(2L);
        CustomForm customForm = mock(CustomForm.class);
        FormUiDto formUi = formUiDtoWithFields(textField);
        CustomFormResponseViewModel responseVm = responseWith(textField, new CustomFieldAnswerTextViewModel());

        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUi);
        when(formService.initOrReuseResponse(isNull(), same(specimen), any(), eq(true))).thenReturn(responseVm);
        when(specimenService.save(same(specimen))).thenReturn(specimen);

        FindPatchRequest request = new FindPatchRequest();
        request.setFieldAnswers(Map.of("2", new AnswerInput("updated", null)));

        FindResource result = service.patchFind(7L, request, personDto, SCOPE, LANG);

        assertThat(result).isSameAs(findResource);
        verify(formService).applyTypedValueToAnswer(any(), eq("updated"));
        verify(formService).updateJpaEntityFromResponse(same(responseVm), same(specimen));
        verify(specimenService).save(specimen);
    }

    @Test
    void patchFind_nullFieldAnswers_returnsWithoutSave() {
        SpecimenDTO specimen = accessibleSpecimen();
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);

        FindPatchRequest request = new FindPatchRequest();
        request.setFieldAnswers(null);

        service.patchFind(7L, request, personDto, SCOPE, LANG);

        verify(specimenService, never()).save(any());
    }

    // --- deleteFind ---

    @Test
    void deleteFind_notFound_throws404() {
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteFind(7L, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteFind_withoutInstitution_throws400() {
        SpecimenDTO specimen = accessibleSpecimen();
        specimen.setCreatedByInstitution(null);
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));

        assertThatThrownBy(() -> service.deleteFind(7L, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Mobilier sans organisation");
    }

    @Test
    void deleteFind_withoutRecordingUnit_throws400() {
        SpecimenDTO specimen = accessibleSpecimen();
        specimen.setRecordingUnit(new RecordingUnitSummaryDTO());
        specimen.getRecordingUnit().setId(null);
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));

        assertThatThrownBy(() -> service.deleteFind(7L, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Mobilier sans unité d'enregistrement");
    }

    @Test
    void deleteFind_withoutWritePermission_throws403() {
        SpecimenDTO specimen = accessibleSpecimen();
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);
        when(profilePermissionService.hasRecordingUnitWritePermission(any(), any(RecordingUnitDTO.class))).thenReturn(false);

        assertThatThrownBy(() -> service.deleteFind(7L, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void deleteFind_success_deletesSpecimen() {
        SpecimenDTO specimen = accessibleSpecimen();
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);

        service.deleteFind(7L, personDto, SCOPE, LANG);

        verify(specimenService).deleteSpecimenById(7L);
    }

    @Test
    void deleteFind_illegalState_throws409() {
        SpecimenDTO specimen = accessibleSpecimen();
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);
        doThrow(new IllegalStateException("lié")).when(specimenService).deleteSpecimenById(7L);

        assertThatThrownBy(() -> service.deleteFind(7L, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deleteFind_illegalArgument_throws404() {
        SpecimenDTO specimen = accessibleSpecimen();
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(specimen));
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(42L, SCOPE)).thenReturn(recordingUnit);
        doThrow(new IllegalArgumentException("absent")).when(specimenService).deleteSpecimenById(7L);

        assertThatThrownBy(() -> service.deleteFind(7L, personDto, SCOPE, LANG))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- helpers ---

    private void stubTypeConcept() {
        Concept concept = new Concept();
        concept.setId(3L);
        when(conceptRepository.findById(3L)).thenReturn(Optional.of(concept));
        when(conceptMapper.convert(concept)).thenReturn(typeDto);
    }

    private static FindCreateRequest createRequest(String ruKey, String typeConceptId) {
        FindCreateRequest request = new FindCreateRequest();
        request.setRecordingUnitId(ruKey);
        request.setTypeId(typeConceptId);
        return request;
    }

    private SpecimenDTO accessibleSpecimen() {
        SpecimenDTO specimen = new SpecimenDTO();
        specimen.setId(7L);
        specimen.setCreatedByInstitution(institution);
        specimen.setType(typeDto);
        RecordingUnitSummaryDTO ruSum = new RecordingUnitSummaryDTO();
        ruSum.setId(42L);
        specimen.setRecordingUnit(ruSum);
        return specimen;
    }

    private static CustomFormResponseViewModel responseWith(CustomField field, CustomFieldAnswerViewModel vm) {
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(new HashMap<>(Map.of(field, vm)));
        return responseVm;
    }

    private static CustomFieldInteger integerField(long id) {
        CustomFieldInteger f = new CustomFieldInteger();
        f.setId(id);
        return f;
    }

    private static CustomFieldText textField(long id) {
        CustomFieldText f = new CustomFieldText();
        f.setId(id);
        return f;
    }

    private static CustomFieldDateTime dateTimeField(long id) {
        CustomFieldDateTime f = new CustomFieldDateTime();
        f.setId(id);
        return f;
    }

    private static CustomFieldSelectOneFromFieldCode conceptField(long id) {
        CustomFieldSelectOneFromFieldCode f = new CustomFieldSelectOneFromFieldCode();
        f.setId(id);
        return f;
    }

    private static CustomFieldSelectOnePerson personField(long id) {
        CustomFieldSelectOnePerson f = new CustomFieldSelectOnePerson();
        f.setId(id);
        return f;
    }

    private static CustomFieldSelectOneActionUnit projectField(long id) {
        CustomFieldSelectOneActionUnit f = new CustomFieldSelectOneActionUnit();
        f.setId(id);
        return f;
    }

    private static CustomFieldSelectOneSpatialUnit spatialField(long id) {
        CustomFieldSelectOneSpatialUnit f = new CustomFieldSelectOneSpatialUnit();
        f.setId(id);
        return f;
    }

    private static CustomFieldSelectOne unsupportedField(long id) {
        CustomFieldSelectOne f = new CustomFieldSelectOne();
        f.setId(id);
        return f;
    }

    private static FormUiDto formUiDtoWithFields(CustomField... fields) {
        List<CustomColUiDto> columns = new java.util.ArrayList<>();
        for (CustomField field : fields) {
            CustomColUiDto col = new CustomColUiDto();
            col.setField(field);
            columns.add(col);
        }
        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(columns);
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));
        FormUiDto ui = new FormUiDto();
        ui.setLayout(List.of(panel));
        return ui;
    }
}
