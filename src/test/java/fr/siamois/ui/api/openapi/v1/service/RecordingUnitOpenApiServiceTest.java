package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOne;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.attributeconverter.CustomFormLayoutConverter;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindMobilierFormData;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.*;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingUnitOpenApiServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L);

    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private FormService formService;
    @Mock
    private FieldConfigurationService fieldConfigurationService;
    @Mock
    private RecordingUnitResponseMapper recordingUnitResponseMapper;
    @Mock
    private ConversionService conversionService;
    @Mock
    private CustomFormLayoutConverter customFormLayoutConverter;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private InstitutionService institutionService;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private SpecimenService specimenService;
    @Mock
    private LangService langService;
    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private PersonService personService;
    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private PersonMapper personMapper;

    private RecordingUnitOpenApiService service;

    private PersonDTO personDto;
    private RecordingUnitDTO ruDto;
    private RecordingUnit ruEntity;
    private RecordingUnitResource ruResource;

    @BeforeEach
    void setUp() {
        service = new RecordingUnitOpenApiService(
                recordingUnitService,
                formService,
                fieldConfigurationService,
                recordingUnitResponseMapper,
                conversionService,
                customFormLayoutConverter,
                conceptMapper,
                institutionService,
                conceptRepository,
                specimenService,
                langService,
                actionUnitService,
                permissionService,
                personService,
                spatialUnitService,
                personMapper);

        lenient().when(langService.localeForApiLang(any())).thenAnswer(inv -> {
            Object arg = inv.getArgument(0);
            if (arg == null || ((String) arg).isBlank()) {
                return Locale.FRENCH;
            }
            return Locale.forLanguageTag((String) arg);
        });
        lenient().when(langService.resolveMessage(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        personDto = new PersonDTO();
        personDto.setId(1L);

        ruDto = new RecordingUnitDTO();
        ruDto.setId(1026L);

        ruEntity = mock(RecordingUnit.class);
        ruResource = new RecordingUnitResource();
        ruResource.setResourceType("recording-units");
        ruResource.setId("1026");
    }

    @Test
    void buildMobileDetail_whenInstitutionNull_returnsRecordingUnitOnlyWithoutForm() {
        ruDto.setCreatedByInstitution(null);
        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "fr");

        assertThat(data.form()).isNull();
        assertThat(data.fields()).isEmpty();
        assertThat(data.vocabulariesByFieldCode()).isEmpty();
        assertThat(data.recordingUnit().getId()).isEqualTo("1026");
        verifyNoInteractions(formService, conversionService, customFormLayoutConverter, fieldConfigurationService, conceptMapper);
    }

    @Test
    void buildMobileDetail_whenNoCustomForm_returnsRecordingUnitWithoutFormBundle() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(ruDto.getType(), inst)).thenReturn(null);

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "fr");

        assertThat(data.form()).isNull();
        assertThat(data.fields()).isEmpty();
        assertThat(data.vocabulariesByFieldCode()).isEmpty();
    }

    @Test
    void buildMobileDetail_whenFormPresent_populatesBundleLayoutJsonFieldsAndVocabularies() throws Exception {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(7L);
        when(customForm.getName()).thenReturn("F");
        when(customForm.getDescription()).thenReturn("D");
        when(customForm.getLayout()).thenReturn(List.of());

        CustomFieldSelectOneFromFieldCode vocabField = mock(CustomFieldSelectOneFromFieldCode.class);
        when(vocabField.getId()).thenReturn(99L);
        when(vocabField.getFieldCode()).thenReturn("SIARU.NOTATION");
        when(vocabField.getLabel()).thenReturn("Notation");
        when(vocabField.getHint()).thenReturn(null);
        when(vocabField.getValueBinding()).thenReturn(null);
        when(vocabField.getIsSystemField()).thenReturn(false);

        FormUiDto formUiDtoWithVocab = formUiDtoWithOneField(vocabField);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(ruDto.getType(), inst)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDtoWithVocab);
        when(customFormLayoutConverter.convertToDatabaseColumn(customForm.getLayout())).thenReturn("[]");

        CustomFieldAnswerTextViewModel answerVm = new CustomFieldAnswerTextViewModel();
        answerVm.setValue("hello");
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(vocabField, answerVm);
        responseVm.setAnswers(answers);

        when(formService.initOrReuseResponse(nullable(CustomFormResponseViewModel.class), eq(ruDto), any(), eq(true))).thenReturn(responseVm);
        when(formService.readAnswerValueForApi(answerVm)).thenReturn("hello");

        ConceptAutocompleteDTO opt = mock(ConceptAutocompleteDTO.class);
        when(fieldConfigurationService.fetchAutocomplete(any(UserInfo.class), eq("SIARU.NOTATION"), isNull()))
                .thenReturn(List.of(opt));

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "de");

        RecordingUnitFormBundle form = data.form();
        assertThat(form).isNotNull();
        assertThat(form.formId()).isEqualTo(7L);
        assertThat(form.name()).isEqualTo("F");
        assertThat(form.layoutJson()).isEqualTo("[]");

        assertThat(data.fields()).containsKey("99");
        assertThat(data.fields().get("99").currentValue()).isEqualTo("hello");
        assertThat(data.fields().get("99").fieldCode()).isEqualTo("SIARU.NOTATION");

        assertThat(data.vocabulariesByFieldCode()).containsKey("SIARU.NOTATION");
        assertThat(data.vocabulariesByFieldCode().get("SIARU.NOTATION")).hasSize(1);

        ArgumentCaptor<UserInfo> userCaptor = ArgumentCaptor.forClass(UserInfo.class);
        verify(fieldConfigurationService).fetchAutocomplete(userCaptor.capture(), eq("SIARU.NOTATION"), isNull());
        assertThat(userCaptor.getValue().getLang()).isEqualTo("de");
    }

    @Test
    void buildMobileDetail_whenNoConfigForField_putsEmptyVocabularyList() throws Exception {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(1L);
        when(customForm.getName()).thenReturn("x");
        when(customForm.getDescription()).thenReturn(null);
        when(customForm.getLayout()).thenReturn(List.of());

        CustomFieldSelectOneFromFieldCode vocabField = mock(CustomFieldSelectOneFromFieldCode.class);
        when(vocabField.getId()).thenReturn(1L);
        when(vocabField.getFieldCode()).thenReturn("UNKNOWN.CODE");
        when(vocabField.getLabel()).thenReturn("L");
        when(vocabField.getHint()).thenReturn(null);
        when(vocabField.getValueBinding()).thenReturn(null);
        when(vocabField.getIsSystemField()).thenReturn(false);

        FormUiDto formUiDto = formUiDtoWithOneField(vocabField);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(any(), any())).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");

        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(vocabField, new CustomFieldAnswerTextViewModel());
        responseVm.setAnswers(answers);
        when(formService.initOrReuseResponse(nullable(CustomFormResponseViewModel.class), any(), any(), eq(true))).thenReturn(responseVm);
        when(formService.readAnswerValueForApi(any())).thenReturn(null);

        when(fieldConfigurationService.fetchAutocomplete(any(UserInfo.class), eq("UNKNOWN.CODE"), isNull()))
                .thenThrow(new NoConfigForFieldException("UNKNOWN.CODE"));

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "fr");

        assertThat(data.vocabulariesByFieldCode().get("UNKNOWN.CODE")).isEmpty();
    }

    @Test
    void buildMobileDetail_whenInitOrReuseThrows_returnsMetadataOnlyFieldsWithNullValues() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(1L);
        when(customForm.getLayout()).thenReturn(List.of());

        CustomFieldText textField = mock(CustomFieldText.class);
        when(textField.getId()).thenReturn(200L);
        when(textField.getLabel()).thenReturn("Libellé");
        when(textField.getHint()).thenReturn("h");
        when(textField.getValueBinding()).thenReturn("desc");
        when(textField.getIsSystemField()).thenReturn(false);

        FormUiDto formUiDto = formUiDtoWithOneField(textField);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(any(), any())).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        doThrow(new IllegalStateException("init failed"))
                .when(formService)
                .initOrReuseResponse(nullable(CustomFormResponseViewModel.class), same(ruDto), any(FieldSource.class), eq(true));

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "fr");

        assertThat(data.fields()).containsKey("200");
        assertThat(data.fields().get("200").currentValue()).isNull();
        assertThat(data.fields().get("200").label()).isEqualTo("Libellé");
        assertThat(data.fields().get("200").answerType()).isNotBlank();
    }

    @Test
    void buildMobileDetail_mergesPersistedIntegerAnswer_whenFormMatches() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(10L);
        when(customForm.getLayout()).thenReturn(List.of());

        var intField = mock(fr.siamois.domain.models.form.customfield.CustomFieldInteger.class);
        when(intField.getId()).thenReturn(5L);
        when(intField.getLabel()).thenReturn("n");
        when(intField.getHint()).thenReturn(null);
        when(intField.getValueBinding()).thenReturn("identifier");
        when(intField.getIsSystemField()).thenReturn(false);

        FormUiDto formUiDto = formUiDtoWithOneField(intField);

        CustomFieldAnswerIntegerViewModel answerVm = new CustomFieldAnswerIntegerViewModel();
        answerVm.setValue(null);

        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(intField, answerVm);
        responseVm.setAnswers(answers);

        CustomForm persistedForm = mock(CustomForm.class);
        when(persistedForm.getId()).thenReturn(10L);

        CustomFieldAnswerInteger jpaAnswer = mock(CustomFieldAnswerInteger.class);
        when(jpaAnswer.getValue()).thenReturn(42);

        CustomFormResponse formResponse = mock(CustomFormResponse.class);
        when(formResponse.getForm()).thenReturn(persistedForm);
        Map<CustomField, fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer> persistedAnswers = new HashMap<>();
        persistedAnswers.put(intField, jpaAnswer);
        when(formResponse.getAnswers()).thenReturn(persistedAnswers);

        when(ruEntity.getFormResponse()).thenReturn(formResponse);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(any(), any())).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        when(formService.initOrReuseResponse(nullable(CustomFormResponseViewModel.class), any(), any(), eq(true))).thenReturn(responseVm);
        when(formService.readAnswerValueForApi(same(answerVm))).thenReturn(42);

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "fr");

        assertThat(data.fields().get("5").currentValue()).isEqualTo(42);
        verify(formService).readAnswerValueForApi(same(answerVm));
    }

    @Test
    void buildMobileDetail_mergesPersistedInteger_whenPersistedMapUsesDifferentFieldInstanceWithSameId() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(10L);
        when(customForm.getLayout()).thenReturn(List.of());

        CustomFieldInteger fieldFromLayout = mock(CustomFieldInteger.class);
        when(fieldFromLayout.getId()).thenReturn(5L);
        when(fieldFromLayout.getLabel()).thenReturn("n");
        when(fieldFromLayout.getHint()).thenReturn(null);
        when(fieldFromLayout.getValueBinding()).thenReturn("identifier");
        when(fieldFromLayout.getIsSystemField()).thenReturn(false);

        CustomFieldInteger fieldFromDb = mock(CustomFieldInteger.class);
        when(fieldFromDb.getId()).thenReturn(5L);

        FormUiDto formUiDto = formUiDtoWithOneField(fieldFromLayout);

        CustomFieldAnswerIntegerViewModel answerVm = new CustomFieldAnswerIntegerViewModel();
        answerVm.setValue(null);
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(fieldFromLayout, answerVm);
        responseVm.setAnswers(answers);

        CustomForm persistedForm = mock(CustomForm.class);
        when(persistedForm.getId()).thenReturn(10L);
        CustomFieldAnswerInteger jpaAnswer = mock(CustomFieldAnswerInteger.class);
        when(jpaAnswer.getValue()).thenReturn(99);
        CustomFormResponse formResponse = mock(CustomFormResponse.class);
        when(formResponse.getForm()).thenReturn(persistedForm);
        Map<CustomField, fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer> persistedAnswers = new HashMap<>();
        persistedAnswers.put(fieldFromDb, jpaAnswer);
        when(formResponse.getAnswers()).thenReturn(persistedAnswers);
        when(ruEntity.getFormResponse()).thenReturn(formResponse);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(any(), any())).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        when(formService.initOrReuseResponse(nullable(CustomFormResponseViewModel.class), any(), any(), eq(true))).thenReturn(responseVm);
        when(formService.readAnswerValueForApi(same(answerVm))).thenReturn(99);

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "fr");

        assertThat(data.fields().get("5").currentValue()).isEqualTo(99);
        verifyNoInteractions(conceptMapper);
    }

    @Test
    void buildMobileDetail_mergesPersistedTextAnswer_fromDatabase() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(1L);
        when(customForm.getLayout()).thenReturn(List.of());

        CustomFieldText textFromLayout = mock(CustomFieldText.class);
        when(textFromLayout.getId()).thenReturn(11L);
        when(textFromLayout.getLabel()).thenReturn("Note");
        when(textFromLayout.getHint()).thenReturn(null);
        when(textFromLayout.getValueBinding()).thenReturn(null);
        when(textFromLayout.getIsSystemField()).thenReturn(false);

        FormUiDto formUiDto = formUiDtoWithOneField(textFromLayout);
        CustomFieldAnswerTextViewModel answerVm = new CustomFieldAnswerTextViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(Map.of(textFromLayout, answerVm));

        CustomForm persistedForm = mock(CustomForm.class);
        when(persistedForm.getId()).thenReturn(1L);
        CustomFieldAnswerText jpaText = mock(CustomFieldAnswerText.class);
        when(jpaText.getValue()).thenReturn("saisie utilisateur");

        CustomFieldText textFromDb = mock(CustomFieldText.class);
        when(textFromDb.getId()).thenReturn(11L);

        CustomFormResponse formResponse = mock(CustomFormResponse.class);
        when(formResponse.getForm()).thenReturn(persistedForm);
        when(formResponse.getAnswers()).thenReturn(Map.of(textFromDb, jpaText));
        when(ruEntity.getFormResponse()).thenReturn(formResponse);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(any(), any())).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        when(formService.initOrReuseResponse(nullable(CustomFormResponseViewModel.class), any(), any(), eq(true))).thenReturn(responseVm);
        when(formService.readAnswerValueForApi(same(answerVm))).thenReturn("saisie utilisateur");

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "fr");

        assertThat(data.fields().get("11").currentValue()).isEqualTo("saisie utilisateur");
        verifyNoInteractions(conceptMapper);
    }

    @Test
    void buildMobileDetail_mergesPersistedDateTimeAnswer_fromDatabase() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(2L);
        when(customForm.getLayout()).thenReturn(List.of());

        var dtFieldLayout = mock(fr.siamois.domain.models.form.customfield.CustomFieldDateTime.class);
        when(dtFieldLayout.getId()).thenReturn(12L);
        when(dtFieldLayout.getLabel()).thenReturn("d");
        when(dtFieldLayout.getHint()).thenReturn(null);
        when(dtFieldLayout.getValueBinding()).thenReturn(null);
        when(dtFieldLayout.getIsSystemField()).thenReturn(false);

        var dtFieldDb = mock(fr.siamois.domain.models.form.customfield.CustomFieldDateTime.class);
        when(dtFieldDb.getId()).thenReturn(12L);

        FormUiDto formUiDto = formUiDtoWithOneField(dtFieldLayout);
        CustomFieldAnswerDateTimeViewModel answerVm = new CustomFieldAnswerDateTimeViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(Map.of(dtFieldLayout, answerVm));

        LocalDateTime saved = LocalDateTime.of(2024, Month.JUNE, 1, 14, 30);
        CustomFieldAnswerDateTime jpaDt = mock(CustomFieldAnswerDateTime.class);
        when(jpaDt.getValue()).thenReturn(saved);

        CustomForm persistedForm = mock(CustomForm.class);
        when(persistedForm.getId()).thenReturn(2L);
        CustomFormResponse formResponse = mock(CustomFormResponse.class);
        when(formResponse.getForm()).thenReturn(persistedForm);
        when(formResponse.getAnswers()).thenReturn(Map.of(dtFieldDb, jpaDt));
        when(ruEntity.getFormResponse()).thenReturn(formResponse);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(any(), any())).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        when(formService.initOrReuseResponse(nullable(CustomFormResponseViewModel.class), any(), any(), eq(true))).thenReturn(responseVm);
        when(formService.readAnswerValueForApi(same(answerVm))).thenReturn(saved.atOffset(java.time.ZoneOffset.UTC));

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "fr");

        assertThat(data.fields().get("12").currentValue()).isEqualTo(saved.atOffset(java.time.ZoneOffset.UTC));
        verifyNoInteractions(conceptMapper);
    }

    @Test
    void buildMobileDetail_mergesPersistedSelectOne_intoVocabField_viaConceptMapper() throws Exception {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(3L);
        when(customForm.getLayout()).thenReturn(List.of());

        CustomFieldSelectOneFromFieldCode fieldLayout = mock(CustomFieldSelectOneFromFieldCode.class);
        when(fieldLayout.getId()).thenReturn(88L);
        when(fieldLayout.getFieldCode()).thenReturn("SIARU.X");
        when(fieldLayout.getLabel()).thenReturn("Type");
        when(fieldLayout.getHint()).thenReturn(null);
        when(fieldLayout.getValueBinding()).thenReturn(null);
        when(fieldLayout.getIsSystemField()).thenReturn(false);

        CustomFieldSelectOneFromFieldCode fieldDb = mock(CustomFieldSelectOneFromFieldCode.class);
        when(fieldDb.getId()).thenReturn(88L);

        FormUiDto formUiDto = formUiDtoWithOneField(fieldLayout);
        CustomFieldAnswerSelectOneFromFieldCodeViewModel answerVm = new CustomFieldAnswerSelectOneFromFieldCodeViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(Map.of(fieldLayout, answerVm));

        Concept jpaConcept = mock(Concept.class);
        CustomFieldAnswerSelectOne jpaSel = mock(CustomFieldAnswerSelectOne.class);
        when(jpaSel.getValue()).thenReturn(jpaConcept);

        ConceptDTO conceptDto = new ConceptDTO();
        conceptDto.setExternalId("EXT-42");
        when(conceptMapper.convert(jpaConcept)).thenReturn(conceptDto);

        CustomForm persistedForm = mock(CustomForm.class);
        when(persistedForm.getId()).thenReturn(3L);
        CustomFormResponse formResponse = mock(CustomFormResponse.class);
        when(formResponse.getForm()).thenReturn(persistedForm);
        when(formResponse.getAnswers()).thenReturn(Map.of(fieldDb, jpaSel));
        when(ruEntity.getFormResponse()).thenReturn(formResponse);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(any(), any())).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        when(formService.initOrReuseResponse(nullable(CustomFormResponseViewModel.class), any(), any(), eq(true))).thenReturn(responseVm);
        when(fieldConfigurationService.fetchAutocomplete(any(UserInfo.class), eq("SIARU.X"), isNull())).thenReturn(List.of());
        when(formService.readAnswerValueForApi(same(answerVm))).thenAnswer(inv -> {
            CustomFieldAnswerSelectOneFromFieldCodeViewModel vm = inv.getArgument(0);
            return vm.getValue() != null ? vm.getValue().concept() : null;
        });

        RecordingUnitMobileDetailData data = service.buildMobileDetail("1026", personDto, SCOPE, null, "fr");

        assertThat(data.fields().get("88").currentValue()).isSameAs(conceptDto);
        verify(conceptMapper).convert(jpaConcept);
    }

    @Test
    void buildRecordingUnitRelations_mapsBundleFromRecordingUnitService() {
        StratigraphicRelationshipDTO strat = new StratigraphicRelationshipDTO();
        RecordingUnitSummaryDTO parent = new RecordingUnitSummaryDTO();
        parent.setId(201L);
        RecordingUnitSummaryDTO child = new RecordingUnitSummaryDTO();
        child.setId(202L);
        RecordingUnitService.RecordingUnitRelationsBundle bundle =
                new RecordingUnitService.RecordingUnitRelationsBundle(List.of(strat), List.of(parent), List.of(child));
        when(recordingUnitService.findRelationsForAccessibleRecordingUnit("42", SCOPE)).thenReturn(bundle);

        RecordingUnitRelationsData data = service.buildRecordingUnitRelations("42", SCOPE);

        assertThat(data.stratigraphicRelationships()).containsExactly(strat);
        assertThat(data.parents()).containsExactly(parent);
        assertThat(data.children()).containsExactly(child);
        verify(recordingUnitService).findRelationsForAccessibleRecordingUnit("42", SCOPE);
    }

    @Test
    void buildRecordingUnitChildren_wrapsListFromRecordingUnitService() {
        RecordingUnitSummaryDTO child = new RecordingUnitSummaryDTO();
        child.setId(301L);
        when(recordingUnitService.findChildrenForAccessibleRecordingUnit("5", SCOPE)).thenReturn(List.of(child));

        RecordingUnitChildrenData data = service.buildRecordingUnitChildren("5", SCOPE);

        assertThat(data.children()).containsExactly(child);
        verify(recordingUnitService).findChildrenForAccessibleRecordingUnit("5", SCOPE);
    }

    @Test
    void buildRecordingUnitChildren_emptyListFromService() {
        when(recordingUnitService.findChildrenForAccessibleRecordingUnit("9", SCOPE)).thenReturn(List.of());

        RecordingUnitChildrenData data = service.buildRecordingUnitChildren("9", SCOPE);

        assertThat(data.children()).isEmpty();
        verify(recordingUnitService).findChildrenForAccessibleRecordingUnit("9", SCOPE);
    }

    @Test
    void addExistingChild_linksUnitsAndReturnsRelations() {
        RecordingUnit parentEntity = new RecordingUnit();
        parentEntity.setId(5L);
        RecordingUnitDTO parentDto = new RecordingUnitDTO();
        parentDto.setId(5L);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        parentDto.setCreatedByInstitution(institution);
        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("5"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(parentEntity, parentDto));
        when(permissionService.hasWritePermission(any(UserInfo.class), eq(parentDto))).thenReturn(true);
        when(recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(99L, SCOPE)).thenReturn(new RecordingUnitDTO());
        when(recordingUnitService.findRelationsForAccessibleRecordingUnit("5", SCOPE)).thenReturn(
                new RecordingUnitService.RecordingUnitRelationsBundle(List.of(), List.of(), List.of()));

        RecordingUnitRelationsData data = service.addExistingChild("5", 99L, personDto, SCOPE);

        assertThat(data).isNotNull();
        verify(recordingUnitService).addHierarchyChild(5L, 99L);
        verify(recordingUnitService).findRelationsForAccessibleRecordingUnit("5", SCOPE);
    }

    @Test
    void addExistingChild_withoutWritePermission_throws403() {
        RecordingUnit parentEntity = new RecordingUnit();
        parentEntity.setId(5L);
        RecordingUnitDTO parentDto = new RecordingUnitDTO();
        parentDto.setId(5L);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        parentDto.setCreatedByInstitution(institution);
        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("5"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(parentEntity, parentDto));
        when(permissionService.hasWritePermission(any(UserInfo.class), eq(parentDto))).thenReturn(false);

        assertThatThrownBy(() -> service.addExistingChild("5", 99L, personDto, SCOPE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(HttpStatus.FORBIDDEN.value()));

        verify(recordingUnitService, never()).addHierarchyChild(any(Long.class), any(Long.class));
    }

    @Test
    void buildRecordingUnitCreateForm_unknownOrganization_throws404() {
        when(institutionService.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.buildRecordingUnitCreateForm(10L, 1L, personDto, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void buildRecordingUnitCreateForm_unknownType_throws404() {
        when(institutionService.findById(10L)).thenReturn(new InstitutionDTO());
        when(conceptRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildRecordingUnitCreateForm(10L, 99L, personDto, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void buildRecordingUnitCreateForm_whenNoCustomForm_returnsTypeOnly() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);
        Concept concept = mock(Concept.class);
        when(conceptRepository.findById(5L)).thenReturn(Optional.of(concept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(5L);
        when(conceptMapper.convert(concept)).thenReturn(typeDto);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, inst)).thenReturn(null);

        RecordingUnitCreateFormData data = service.buildRecordingUnitCreateForm(10L, 5L, personDto, "fr");

        assertThat(data.form()).isNull();
        assertThat(data.fields()).isEmpty();
        assertThat(data.vocabulariesByFieldCode()).isEmpty();
        assertThat(data.recordingUnitType().getId()).isEqualTo(5L);
    }

    @Test
    void buildRecordingUnitCreateForm_whenFormPresent_populatesFormFieldsAndVocabularies() throws Exception {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);
        Concept concept = mock(Concept.class);
        when(conceptRepository.findById(7L)).thenReturn(Optional.of(concept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(7L);
        when(conceptMapper.convert(concept)).thenReturn(typeDto);

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(100L);
        when(customForm.getName()).thenReturn("Form A");
        when(customForm.getDescription()).thenReturn("Desc");
        when(customForm.getLayout()).thenReturn(List.of());
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, inst)).thenReturn(customForm);

        CustomFieldSelectOneFromFieldCode vocabField = mock(CustomFieldSelectOneFromFieldCode.class);
        when(vocabField.getId()).thenReturn(55L);
        when(vocabField.getFieldCode()).thenReturn("SIARU.NOTATION");
        when(vocabField.getLabel()).thenReturn("Notation");
        when(vocabField.getHint()).thenReturn(null);
        when(vocabField.getValueBinding()).thenReturn(null);
        when(vocabField.getIsSystemField()).thenReturn(false);

        FormUiDto formUiDto = formUiDtoWithOneField(vocabField);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("{}");

        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = new HashMap<>();
        CustomFieldAnswerTextViewModel answerVm = new CustomFieldAnswerTextViewModel();
        answerVm.setValue("x");
        answers.put(vocabField, answerVm);
        responseVm.setAnswers(answers);
        when(formService.initOrReuseResponse(nullable(CustomFormResponseViewModel.class), any(RecordingUnitDTO.class), any(), eq(true)))
                .thenReturn(responseVm);

        ConceptAutocompleteDTO opt = mock(ConceptAutocompleteDTO.class);
        when(fieldConfigurationService.fetchAutocomplete(any(UserInfo.class), eq("SIARU.NOTATION"), isNull())).thenReturn(List.of(opt));
        when(formService.readAnswerValueForApi(answerVm)).thenReturn("x");

        RecordingUnitCreateFormData data = service.buildRecordingUnitCreateForm(10L, 7L, personDto, "fr");

        assertThat(data.form()).isNotNull();
        assertThat(data.form().formId()).isEqualTo(100L);
        assertThat(data.form().name()).isEqualTo("Form A");
        assertThat(data.fields()).containsKey("55");
        assertThat(data.fields().get("55").fieldCode()).isEqualTo("SIARU.NOTATION");
        assertThat(data.fields().get("55").currentValue()).isEqualTo("x");
        assertThat(data.vocabulariesByFieldCode().get("SIARU.NOTATION")).containsExactly(opt);
    }

    @Test
    void buildRecordingUnitCreateForm_noConfigForFieldCode_returnsEmptyVocabularyList() throws Exception {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);
        Concept concept = mock(Concept.class);
        when(conceptRepository.findById(7L)).thenReturn(Optional.of(concept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(7L);
        when(conceptMapper.convert(concept)).thenReturn(typeDto);

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(1L);
        when(customForm.getName()).thenReturn("F");
        when(customForm.getDescription()).thenReturn(null);
        when(customForm.getLayout()).thenReturn(List.of());
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, inst)).thenReturn(customForm);

        CustomFieldSelectOneFromFieldCode vocabField = mock(CustomFieldSelectOneFromFieldCode.class);
        when(vocabField.getId()).thenReturn(1L);
        when(vocabField.getFieldCode()).thenReturn("SIARU.MISSING");
        when(vocabField.getLabel()).thenReturn("L");
        when(vocabField.getHint()).thenReturn(null);
        when(vocabField.getValueBinding()).thenReturn(null);
        when(vocabField.getIsSystemField()).thenReturn(false);

        FormUiDto formUiDto = formUiDtoWithOneField(vocabField);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");

        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        CustomFieldAnswerTextViewModel answerVm = new CustomFieldAnswerTextViewModel();
        responseVm.setAnswers(Map.of(vocabField, answerVm));
        when(formService.initOrReuseResponse(nullable(CustomFormResponseViewModel.class), any(RecordingUnitDTO.class), any(), eq(true)))
                .thenReturn(responseVm);
        when(formService.readAnswerValueForApi(any())).thenReturn(null);
        when(fieldConfigurationService.fetchAutocomplete(any(UserInfo.class), eq("SIARU.MISSING"), isNull()))
                .thenThrow(new NoConfigForFieldException("SIARU.MISSING"));

        RecordingUnitCreateFormData data = service.buildRecordingUnitCreateForm(10L, 7L, personDto, "fr");

        assertThat(data.vocabulariesByFieldCode().get("SIARU.MISSING")).isEmpty();
    }

    @Test
    void buildRecordingUnitCreateForm_whenInitThrows_fallsBackToMetadataOnlyFields() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);
        Concept concept = mock(Concept.class);
        when(conceptRepository.findById(2L)).thenReturn(Optional.of(concept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(2L);
        when(conceptMapper.convert(concept)).thenReturn(typeDto);

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(50L);
        when(customForm.getName()).thenReturn("N");
        when(customForm.getDescription()).thenReturn(null);
        when(customForm.getLayout()).thenReturn(List.of());
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, inst)).thenReturn(customForm);

        // Entité réelle : les mocks n'ont pas @DiscriminatorValue sur leur classe → answerType ≠ "TEXT" dans toFieldApi.
        CustomFieldText textField = new CustomFieldText();
        textField.setId(88L);
        textField.setLabel("Titre");
        textField.setHint(null);
        textField.setValueBinding(null);
        textField.setIsSystemField(false);

        FormUiDto formUiDto = formUiDtoWithOneField(textField);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        doThrow(new IllegalStateException("boom"))
                .when(formService)
                .initOrReuseResponse(nullable(CustomFormResponseViewModel.class), any(RecordingUnitDTO.class), any(FieldSource.class), eq(true));

        RecordingUnitCreateFormData data = service.buildRecordingUnitCreateForm(10L, 2L, personDto, "fr");

        assertThat(data.form()).isNotNull();
        assertThat(data.form().formId()).isEqualTo(50L);
        assertThat(data.fields()).containsKey("88");
        assertThat(data.fields().get("88").answerType()).isEqualTo("TEXT");
        assertThat(data.fields().get("88").currentValue()).isNull();
        assertThat(data.vocabulariesByFieldCode()).isEmpty();
    }

    @Test
    void buildProjectUiForm_unknownOrganization_throws404() {
        when(institutionService.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.buildProjectUiForm(10L, personDto, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void buildProjectUiForm_returnsMetadataOnly() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);

        CustomFieldText textField = new CustomFieldText();
        textField.setId(301L);
        textField.setLabel("Libellé projet");
        textField.setHint(null);
        textField.setValueBinding(null);
        textField.setIsSystemField(true);

        FormUiDto formUiDto = formUiDtoWithOneField(textField);
        when(conversionService.convert(ActionUnit.DETAILS_FORM, FormUiDto.class)).thenReturn(formUiDto);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");

        ProjectFormData data = service.buildProjectUiForm(10L, personDto, "fr");

        assertThat(data.form()).isNotNull();
        assertThat(data.fields()).containsKey("301");
        assertThat(data.fields().get("301").currentValue()).isNull();
        assertThat(data.fields().get("301").label()).isEqualTo("Libellé projet");
    }

    @Test
    void buildFindMobilierUiForm_unknownOrganization_throws404() {
        when(institutionService.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.buildFindUiForm(10L, personDto, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void buildFindMobilierUiForm_whenNoCustomForm_returnsEmptyFields() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(null, inst)).thenReturn(null);

        FindMobilierFormData data = service.buildFindUiForm(10L, personDto, "fr");

        assertThat(data.form()).isNull();
        assertThat(data.fields()).isEmpty();
    }

    @Test
    void buildFindMobilierForm_whenNotAccessible_throws404() {
        when(specimenService.findAccessibleByKey("404", SCOPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildFindMobilierForm("404", personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void buildFindMobilierForm_whenNoCustomForm_returnsEmptyForm() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ConceptDTO type = new ConceptDTO();
        type.setId(3L);
        SpecimenDTO spec = new SpecimenDTO();
        spec.setId(7L);
        spec.setCreatedByInstitution(inst);
        spec.setType(type);
        when(specimenService.findAccessibleByKey("7", SCOPE)).thenReturn(Optional.of(spec));
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(type, inst)).thenReturn(null);

        FindMobilierFormData data = service.buildFindMobilierForm("7", personDto, SCOPE, "fr");

        assertThat(data.form()).isNull();
        assertThat(data.fields()).isEmpty();
    }

    @Test
    void patchRecordingUnit_syncRevisionMismatch_throwsConflict() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());
        when(ruEntity.getSyncRevision()).thenReturn(2L);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(ruDto.getType(), inst)).thenReturn(null);

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();
        request.setExpectedRevision(1L);
        request.setFieldAnswers(Map.of());

        assertThatThrownBy(() -> service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr"))
                .isInstanceOf(SyncRevisionConflictException.class)
                .satisfies(ex -> {
                    SyncRevisionConflictException conflict = (SyncRevisionConflictException) ex;
                    assertThat(conflict.getConflictData().currentRevision()).isEqualTo(2L);
                    assertThat(conflict.getConflictData().expectedRevision()).isEqualTo(1L);
                });
    }

    @Test
    void patchRecordingUnit_matchingSyncRevisionWithEmptyAnswers_returnsDetail() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());
        when(ruEntity.getSyncRevision()).thenReturn(3L);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(ruDto.getType(), inst)).thenReturn(null);
        when(permissionService.hasWritePermission(any(), same(ruDto))).thenReturn(true);

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();
        request.setExpectedRevision(3L);

        RecordingUnitMobileDetailData data = service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr");

        assertThat(data.recordingUnit().getId()).isEqualTo("1026");
        verify(recordingUnitService, never()).save(any());
    }

    @Test
    void patchRecordingUnit_withoutWritePermission_throws403() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(permissionService.hasWritePermission(any(), same(ruDto))).thenReturn(false);

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();

        assertThatThrownBy(() -> service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void applySystemProjectFormFieldAnswers_emptyAnswers_noOp() {
        ActionUnitDTO shell = new ActionUnitDTO();
        service.applySystemProjectFormFieldAnswers(shell, Map.of(), personDto, "fr");
        verifyNoInteractions(formService);
    }

    @Test
    void applySystemProjectFormFieldAnswers_withoutOrganization_throws400() {
        ActionUnitDTO shell = new ActionUnitDTO();
        Map<String, Object> fieldAnswers = Map.of("1", "x");
        assertThatThrownBy(() -> service.applySystemProjectFormFieldAnswers(shell, fieldAnswers, personDto, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void applySystemProjectFormFieldAnswers_appliesAnswersOnShell() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ActionUnitDTO shell = new ActionUnitDTO();
        shell.setCreatedByInstitution(inst);

        CustomFieldText textField = mock(CustomFieldText.class);
        when(textField.getId()).thenReturn(11L);
        FormUiDto formUiDto = formUiDtoWithOneField(textField);
        when(conversionService.convert(ActionUnit.DETAILS_FORM, FormUiDto.class)).thenReturn(formUiDto);

        CustomFieldAnswerTextViewModel answerVm = new CustomFieldAnswerTextViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(textField, answerVm);
        responseVm.setAnswers(answers);
        when(formService.initOrReuseResponse(isNull(), same(shell), any(FieldSource.class), eq(true))).thenReturn(responseVm);

        service.applySystemProjectFormFieldAnswers(shell, Map.of("11", "projet"), personDto, "fr");

        verify(formService).applyTypedValueToAnswer(same(answerVm), eq("projet"));
        verify(formService).updateJpaEntityFromResponse(responseVm, shell);
    }

    @Test
    void buildFindMobilierForm_withoutOrganization_throws400() {
        SpecimenDTO spec = new SpecimenDTO();
        spec.setId(1L);
        spec.setType(new ConceptDTO());
        when(specimenService.findAccessibleByKey("1", SCOPE)).thenReturn(Optional.of(spec));

        assertThatThrownBy(() -> service.buildFindMobilierForm("1", personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void buildFindMobilierForm_withoutType_throws400() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        SpecimenDTO spec = new SpecimenDTO();
        spec.setId(1L);
        spec.setCreatedByInstitution(inst);
        when(specimenService.findAccessibleByKey("1", SCOPE)).thenReturn(Optional.of(spec));

        assertThatThrownBy(() -> service.buildFindMobilierForm("1", personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void buildFindMobilierUiForm_whenFormPresent_returnsBundleAndFields() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        when(institutionService.findById(10L)).thenReturn(inst);

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(3L);
        when(customForm.getName()).thenReturn("Mobilier");
        when(customForm.getDescription()).thenReturn("desc");
        when(customForm.getLayout()).thenReturn(List.of());
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(null, inst)).thenReturn(customForm);

        CustomFieldText textField = mock(CustomFieldText.class);
        when(textField.getId()).thenReturn(20L);
        when(textField.getLabel()).thenReturn("Libellé");
        when(textField.getHint()).thenReturn(null);
        when(textField.getValueBinding()).thenReturn(null);
        when(textField.getIsSystemField()).thenReturn(false);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDtoWithOneField(textField));
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");

        FindMobilierFormData data = service.buildFindUiForm(10L, personDto, "fr");

        assertThat(data.form()).isNotNull();
        assertThat(data.form().formId()).isEqualTo(3L);
        assertThat(data.fields()).containsKey("20");
    }

    @Test
    void createRecordingUnit_projectWithoutOrganization_throws400() {
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        when(actionUnitService.findAccessibleProjectByKey("5", SCOPE))
                .thenReturn(new AccessibleProjectForApi(au, 0, 0));

        RecordingUnitCreateRequest request = new RecordingUnitCreateRequest();
        request.setActionUnitId("5");
        request.setRecordingUnitTypeConceptId(42L);

        assertThatThrownBy(() -> service.createRecordingUnit(request, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void patchRecordingUnit_nullCurrentRevision_treatsAsZero() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());
        when(ruEntity.getSyncRevision()).thenReturn(null);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(ruDto.getType(), inst)).thenReturn(null);
        when(permissionService.hasWritePermission(any(), same(ruDto))).thenReturn(true);

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();
        request.setExpectedRevision(0L);

        RecordingUnitMobileDetailData data = service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr");

        assertThat(data.recordingUnit().getId()).isEqualTo("1026");
    }

    @Test
    void createRecordingUnit_missingTypeConceptId_throws400() {
        RecordingUnitCreateRequest request = new RecordingUnitCreateRequest();
        request.setActionUnitId("proj");

        assertThatThrownBy(() -> service.createRecordingUnit(request, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createRecordingUnit_withoutWritePermission_throws403() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        au.setCreatedByInstitution(inst);
        when(actionUnitService.findAccessibleProjectByKey("5", SCOPE))
                .thenReturn(new AccessibleProjectForApi(au, 0, 0));
        when(permissionService.hasWritePermission(any(UserInfo.class), same(au))).thenReturn(false);

        RecordingUnitCreateRequest request = new RecordingUnitCreateRequest();
        request.setActionUnitId("5");
        request.setRecordingUnitTypeConceptId(42L);

        assertThatThrownBy(() -> service.createRecordingUnit(request, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createRecordingUnit_noCustomForm_throws400() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        au.setCreatedByInstitution(inst);
        when(actionUnitService.findAccessibleProjectByKey("5", SCOPE))
                .thenReturn(new AccessibleProjectForApi(au, 0, 0));
        when(permissionService.hasWritePermission(any(UserInfo.class), same(au))).thenReturn(true);
        Concept typeConcept = new Concept();
        typeConcept.setId(42L);
        when(conceptRepository.findById(42L)).thenReturn(Optional.of(typeConcept));
        when(conceptMapper.convert(typeConcept)).thenReturn(new ConceptDTO());
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(any(), eq(inst))).thenReturn(null);

        RecordingUnitCreateRequest request = new RecordingUnitCreateRequest();
        request.setActionUnitId("5");
        request.setRecordingUnitTypeConceptId(42L);

        assertThatThrownBy(() -> service.createRecordingUnit(request, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createRecordingUnit_success_persistsAndReturnsDetail() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        au.setCreatedByInstitution(inst);
        au.setRecordingUnitIdentifierFormat("RU-%s");
        when(actionUnitService.findAccessibleProjectByKey("5", SCOPE))
                .thenReturn(new AccessibleProjectForApi(au, 0, 0));
        when(permissionService.hasWritePermission(any(UserInfo.class), same(au))).thenReturn(true);

        Concept typeConcept = new Concept();
        typeConcept.setId(42L);
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(42L);
        when(conceptRepository.findById(42L)).thenReturn(Optional.of(typeConcept));
        when(conceptMapper.convert(typeConcept)).thenReturn(typeDto);

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getLayout()).thenReturn(List.of());
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, inst)).thenReturn(customForm);

        SpatialUnitSummaryDTO su = new SpatialUnitSummaryDTO();
        su.setId(77L);
        when(spatialUnitService.getSpatialUnitOptionsFor(any(RecordingUnitDTO.class))).thenReturn(List.of(su));

        CustomFieldInteger intField = mock(CustomFieldInteger.class);
        when(intField.getId()).thenReturn(8L);
        when(intField.getLabel()).thenReturn("n");
        when(intField.getHint()).thenReturn(null);
        when(intField.getValueBinding()).thenReturn(null);
        when(intField.getIsSystemField()).thenReturn(false);
        FormUiDto formUiDto = formUiDtoWithOneField(intField);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);

        CustomFieldAnswerIntegerViewModel answerVm = new CustomFieldAnswerIntegerViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(intField, answerVm);
        responseVm.setAnswers(answers);
        when(formService.initOrReuseResponse(isNull(), any(RecordingUnitDTO.class), any(FieldSource.class), eq(true)))
                .thenReturn(responseVm);

        RecordingUnitDTO saved = new RecordingUnitDTO();
        saved.setId(3000L);
        saved.setActionUnit(new ActionUnitSummaryDTO(au));
        when(recordingUnitService.save(any(RecordingUnitDTO.class))).thenReturn(saved);
        when(recordingUnitService.generateFullIdentifier(any(ActionUnitSummaryDTO.class), any())).thenReturn("RU-3000");
        when(recordingUnitService.fullIdentifierAlreadyExistInAction(saved)).thenReturn(false);

        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(typeDto);
        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("3000"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, inst)).thenReturn(customForm);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        when(formService.readAnswerValueForApi(any())).thenReturn(null);

        RecordingUnitCreateRequest request = new RecordingUnitCreateRequest();
        request.setActionUnitId("5");
        request.setRecordingUnitTypeConceptId(42L);
        request.setFieldAnswers(Map.of("8", 12));

        RecordingUnitMobileDetailData data = service.createRecordingUnit(request, personDto, SCOPE, "fr");

        assertThat(data.recordingUnit().getId()).isEqualTo("1026");
        verify(formService).applyTypedValueToAnswer(same(answerVm), eq(12));
        verify(recordingUnitService, org.mockito.Mockito.times(2)).save(any(RecordingUnitDTO.class));
    }

    @Test
    void createRecordingUnit_saveFailure_throws500() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(5L);
        au.setCreatedByInstitution(inst);
        when(actionUnitService.findAccessibleProjectByKey("5", SCOPE))
                .thenReturn(new AccessibleProjectForApi(au, 0, 0));
        when(permissionService.hasWritePermission(any(UserInfo.class), same(au))).thenReturn(true);
        Concept typeConcept = new Concept();
        typeConcept.setId(42L);
        ConceptDTO typeDto = new ConceptDTO();
        when(conceptRepository.findById(42L)).thenReturn(Optional.of(typeConcept));
        when(conceptMapper.convert(typeConcept)).thenReturn(typeDto);
        CustomForm customForm = mock(CustomForm.class);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, inst)).thenReturn(customForm);
        when(spatialUnitService.getSpatialUnitOptionsFor(any())).thenReturn(List.of());
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(new FormUiDto());
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(Map.of());
        when(formService.initOrReuseResponse(isNull(), any(), any(), eq(true))).thenReturn(responseVm);
        when(recordingUnitService.save(any(RecordingUnitDTO.class)))
                .thenThrow(new FailedRecordingUnitSaveException("fail"));

        RecordingUnitCreateRequest request = new RecordingUnitCreateRequest();
        request.setActionUnitId("5");
        request.setRecordingUnitTypeConceptId(42L);

        assertThatThrownBy(() -> service.createRecordingUnit(request, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void patchRecordingUnit_withoutOrganization_throws400() {
        ruDto.setCreatedByInstitution(null);
        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));

        var patchRequest = new RecordingUnitPatchRequest();
        assertThatThrownBy(() -> service.patchRecordingUnit("1026", patchRequest, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void patchRecordingUnit_noCustomFormWithAnswers_throws400() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());
        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(permissionService.hasWritePermission(any(), same(ruDto))).thenReturn(true);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(ruDto.getType(), inst)).thenReturn(null);

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();
        request.setFieldAnswers(Map.of("1", "x"));

        assertThatThrownBy(() -> service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void patchRecordingUnit_withFieldAnswers_persistsAndReturnsDetail() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ConceptDTO type = new ConceptDTO();
        type.setId(3L);
        ruDto.setType(type);

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getId()).thenReturn(10L);
        when(customForm.getLayout()).thenReturn(List.of());

        CustomFieldText textField = mock(CustomFieldText.class);
        when(textField.getId()).thenReturn(15L);
        when(textField.getLabel()).thenReturn("desc");
        when(textField.getHint()).thenReturn(null);
        when(textField.getValueBinding()).thenReturn("description");
        when(textField.getIsSystemField()).thenReturn(false);
        FormUiDto formUiDto = formUiDtoWithOneField(textField);

        CustomFieldAnswerTextViewModel answerVm = new CustomFieldAnswerTextViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(textField, answerVm);
        responseVm.setAnswers(answers);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(eq("1026"), eq(SCOPE), isNull()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(permissionService.hasWritePermission(any(), same(ruDto))).thenReturn(true);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(type, inst)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(formService.initOrReuseResponse(isNull(), same(ruDto), any(FieldSource.class), eq(true))).thenReturn(responseVm);
        when(recordingUnitService.save(ruDto)).thenReturn(ruDto);
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        when(formService.readAnswerValueForApi(same(answerVm))).thenReturn("patched");

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();
        request.setFieldAnswers(Map.of("15", "patched", "bad-key", "ignored"));

        RecordingUnitMobileDetailData data = service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr");

        assertThat(data.recordingUnit().getId()).isEqualTo("1026");
        verify(formService).applyTypedValueToAnswer(same(answerVm), eq("patched"));
        verify(recordingUnitService).save(ruDto);
    }

    @Test
    void patchRecordingUnit_coercesDateTimeAndConceptAnswers() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ConceptDTO type = new ConceptDTO();
        type.setId(3L);
        ruDto.setType(type);

        CustomForm customForm = mock(CustomForm.class);
        when(customForm.getLayout()).thenReturn(List.of());

        CustomFieldDateTime dateField = mock(CustomFieldDateTime.class);
        when(dateField.getId()).thenReturn(21L);
        when(dateField.getLabel()).thenReturn("date");
        when(dateField.getHint()).thenReturn(null);
        when(dateField.getValueBinding()).thenReturn(null);
        when(dateField.getIsSystemField()).thenReturn(false);

        CustomFieldSelectOneFromFieldCode conceptField = mock(CustomFieldSelectOneFromFieldCode.class);
        when(conceptField.getId()).thenReturn(22L);
        when(conceptField.getLabel()).thenReturn("concept");
        when(conceptField.getHint()).thenReturn(null);
        when(conceptField.getValueBinding()).thenReturn(null);
        when(conceptField.getIsSystemField()).thenReturn(false);

        FormUiDto formUiDto = new FormUiDto();
        CustomColUiDto col1 = new CustomColUiDto();
        col1.setField(dateField);
        CustomColUiDto col2 = new CustomColUiDto();
        col2.setField(conceptField);
        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(List.of(col1, col2));
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));
        formUiDto.setLayout(List.of(panel));

        CustomFieldAnswerDateTimeViewModel dateVm = new CustomFieldAnswerDateTimeViewModel();
        CustomFieldAnswerSelectOneFromFieldCodeViewModel conceptVm = new CustomFieldAnswerSelectOneFromFieldCodeViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        Map<CustomField, fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(dateField, dateVm);
        answers.put(conceptField, conceptVm);
        responseVm.setAnswers(answers);

        OffsetDateTime parsed = OffsetDateTime.parse("2025-05-19T10:00:00Z");
        Concept concept = new Concept();
        concept.setId(99L);
        ConceptDTO conceptDto = new ConceptDTO();
        conceptDto.setId(99L);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(permissionService.hasWritePermission(any(), same(ruDto))).thenReturn(true);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(type, inst)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDto);
        when(formService.initOrReuseResponse(isNull(), same(ruDto), any(FieldSource.class), eq(true))).thenReturn(responseVm);
        when(conceptRepository.findById(99L)).thenReturn(Optional.of(concept));
        when(conceptMapper.convert(concept)).thenReturn(conceptDto);
        when(recordingUnitService.save(ruDto)).thenReturn(ruDto);
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        when(formService.readAnswerValueForApi(any())).thenReturn(null);

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();
        request.setFieldAnswers(Map.of(
                "21", "2025-05-19T10:00:00Z",
                "22", Map.of("id", 99)));

        service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr");

        verify(formService).applyTypedValueToAnswer(same(dateVm), eq(parsed));
        verify(formService).applyTypedValueToAnswer(same(conceptVm), eq(conceptDto));
    }

    @Test
    void patchRecordingUnit_unknownConcept_throws400() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ConceptDTO type = new ConceptDTO();
        ruDto.setType(type);

        CustomForm customForm = mock(CustomForm.class);
        CustomFieldSelectOneFromFieldCode conceptField = mock(CustomFieldSelectOneFromFieldCode.class);
        when(conceptField.getId()).thenReturn(22L);

        CustomFieldAnswerSelectOneFromFieldCodeViewModel conceptVm = new CustomFieldAnswerSelectOneFromFieldCodeViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(Map.of(conceptField, conceptVm));

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(permissionService.hasWritePermission(any(), same(ruDto))).thenReturn(true);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(type, inst)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDtoWithOneField(conceptField));
        when(formService.initOrReuseResponse(isNull(), any(), any(FieldSource.class), eq(true))).thenReturn(responseVm);
        when(conceptRepository.findById(404L)).thenReturn(Optional.empty());

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();
        request.setFieldAnswers(Map.of("22", 404L));

        assertThatThrownBy(() -> service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void patchRecordingUnit_saveFailure_throws500() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ruDto.setType(new ConceptDTO());
        CustomForm customForm = mock(CustomForm.class);
        CustomFieldText textField = mock(CustomFieldText.class);
        when(textField.getId()).thenReturn(1L);
        CustomFieldAnswerTextViewModel answerVm = new CustomFieldAnswerTextViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(Map.of(textField, answerVm));

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(permissionService.hasWritePermission(any(), same(ruDto))).thenReturn(true);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(any(), any())).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDtoWithOneField(textField));
        when(formService.initOrReuseResponse(isNull(), any(), any(FieldSource.class), eq(true))).thenReturn(responseVm);
        when(recordingUnitService.save(any(RecordingUnitDTO.class)))
                .thenThrow(new FailedRecordingUnitSaveException("err"));

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();
        request.setFieldAnswers(Map.of("1", "x"));

        assertThatThrownBy(() -> service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void patchRecordingUnit_coercesPersonSelectOne() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ruDto.setCreatedByInstitution(inst);
        ConceptDTO type = new ConceptDTO();
        ruDto.setType(type);

        CustomForm customForm = mock(CustomForm.class);
        CustomFieldSelectOnePerson personField = mock(CustomFieldSelectOnePerson.class);
        when(personField.getId()).thenReturn(30L);
        when(personField.getLabel()).thenReturn("author");
        when(personField.getHint()).thenReturn(null);
        when(personField.getValueBinding()).thenReturn(null);
        when(personField.getIsSystemField()).thenReturn(false);

        CustomFieldAnswerSelectOnePersonViewModel personVm = new CustomFieldAnswerSelectOnePersonViewModel();
        CustomFormResponseViewModel responseVm = new CustomFormResponseViewModel();
        responseVm.setAnswers(Map.of(personField, personVm));

        Person person = new Person();
        person.setId(4L);
        PersonDTO personResult = new PersonDTO();
        personResult.setId(4L);

        when(recordingUnitService.findAccessibleRecordingUnitWithEntity(any(), any(), any()))
                .thenReturn(new RecordingUnitService.AccessibleRecordingUnit(ruEntity, ruDto));
        when(permissionService.hasWritePermission(any(), same(ruDto))).thenReturn(true);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(type, inst)).thenReturn(customForm);
        when(conversionService.convert(customForm, FormUiDto.class)).thenReturn(formUiDtoWithOneField(personField));
        when(formService.initOrReuseResponse(isNull(), same(ruDto), any(FieldSource.class), eq(true))).thenReturn(responseVm);
        when(personService.findById(4L)).thenReturn(person);
        when(personMapper.convert(person)).thenReturn(personResult);
        when(recordingUnitService.save(ruDto)).thenReturn(ruDto);
        when(recordingUnitResponseMapper.convert(ruDto)).thenReturn(ruResource);
        when(customFormLayoutConverter.convertToDatabaseColumn(any())).thenReturn("[]");
        when(formService.readAnswerValueForApi(any())).thenReturn(null);

        RecordingUnitPatchRequest request = new RecordingUnitPatchRequest();
        request.setFieldAnswers(Map.of("30", "4"));

        service.patchRecordingUnit("1026", request, personDto, SCOPE, "fr");

        verify(formService).applyTypedValueToAnswer(same(personVm), eq(personResult));
    }

    private static FormUiDto formUiDtoWithOneField(CustomField field) {
        CustomColUiDto col = new CustomColUiDto();
        col.setField(field);
        CustomRowUiDto row = new CustomRowUiDto();
        row.setColumns(List.of(col));
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setRows(List.of(row));
        FormUiDto ui = new FormUiDto();
        ui.setLayout(List.of(panel));
        return ui;
    }

}
