package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOne;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.attributeconverter.CustomFormLayoutConverter;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitChildrenData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormBundle;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMobileDetailData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsData;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerDateTimeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerIntegerViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectOneFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
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
import java.util.Locale;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

        LocalDateTime saved = LocalDateTime.of(2024, 6, 1, 14, 30);
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
        when(recordingUnitService.findRelationsForAccessibleRecordingUnit(eq("42"), eq(SCOPE))).thenReturn(bundle);

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
        when(recordingUnitService.findChildrenForAccessibleRecordingUnit(eq("5"), eq(SCOPE))).thenReturn(List.of(child));

        RecordingUnitChildrenData data = service.buildRecordingUnitChildren("5", SCOPE);

        assertThat(data.children()).containsExactly(child);
        verify(recordingUnitService).findChildrenForAccessibleRecordingUnit("5", SCOPE);
    }

    @Test
    void buildRecordingUnitChildren_emptyListFromService() {
        when(recordingUnitService.findChildrenForAccessibleRecordingUnit(eq("9"), eq(SCOPE))).thenReturn(List.of());

        RecordingUnitChildrenData data = service.buildRecordingUnitChildren("9", SCOPE);

        assertThat(data.children()).isEmpty();
        verify(recordingUnitService).findChildrenForAccessibleRecordingUnit("9", SCOPE);
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
    void buildFindCreateForm_whenNoInstitution_throws400() {
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(12L);
        ru.setCreatedByInstitution(null);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("12", SCOPE, null)).thenReturn(ru);

        assertThatThrownBy(() -> service.buildFindCreateForm("12", "42", personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void buildFindCreateForm_whenForbidden_throws403() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(12L);
        ru.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("12", SCOPE, null)).thenReturn(ru);
        when(permissionService.hasWritePermission(any(), eq(ru))).thenReturn(false);

        assertThatThrownBy(() -> service.buildFindCreateForm("12", "42", personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void buildFindCreateForm_unknownType_throws404() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(12L);
        ru.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("12", SCOPE, null)).thenReturn(ru);
        when(permissionService.hasWritePermission(any(), eq(ru))).thenReturn(true);
        when(conceptRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildFindCreateForm("12", "99", personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void buildFindCreateForm_whenNoCustomForm_returnsTypeOnly() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(12L);
        ru.setCreatedByInstitution(inst);
        when(recordingUnitService.findAccessibleRecordingUnitByKey("12", SCOPE, null)).thenReturn(ru);
        when(permissionService.hasWritePermission(any(), eq(ru))).thenReturn(true);

        Concept concept = mock(Concept.class);
        when(conceptRepository.findById(42L)).thenReturn(Optional.of(concept));
        ConceptDTO typeDto = new ConceptDTO();
        typeDto.setId(42L);
        when(conceptMapper.convert(concept)).thenReturn(typeDto);
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, inst)).thenReturn(null);

        FindFormData data = service.buildFindCreateForm("12", "42", personDto, SCOPE, "fr");

        assertThat(data.specimenType().getId()).isEqualTo(42L);
        assertThat(data.form()).isNull();
        assertThat(data.fields()).isEmpty();
        assertThat(data.vocabulariesByFieldCode()).isEmpty();
    }

    @Test
    void buildFindForm_whenNotAccessible_throws404() {
        when(specimenService.findAccessibleById(404L, SCOPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildFindForm(404L, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void buildFindForm_whenNoInstitution_throws400() {
        SpecimenDTO spec = new SpecimenDTO();
        spec.setId(1L);
        spec.setCreatedByInstitution(null);
        when(specimenService.findAccessibleById(1L, SCOPE)).thenReturn(Optional.of(spec));

        assertThatThrownBy(() -> service.buildFindForm(1L, personDto, SCOPE, "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void buildFindForm_whenNoCustomForm_returnsTypeOnly() {
        InstitutionDTO inst = new InstitutionDTO();
        inst.setId(10L);
        ConceptDTO type = new ConceptDTO();
        type.setId(3L);
        SpecimenDTO spec = new SpecimenDTO();
        spec.setId(7L);
        spec.setCreatedByInstitution(inst);
        spec.setType(type);
        when(specimenService.findAccessibleById(7L, SCOPE)).thenReturn(Optional.of(spec));
        when(formService.findCustomFormByRecordingUnitTypeAndInstitutionId(type, inst)).thenReturn(null);

        FindFormData data = service.buildFindForm(7L, personDto, SCOPE, "fr");

        assertThat(data.specimenType().getId()).isEqualTo(3L);
        assertThat(data.form()).isNull();
        assertThat(data.fields()).isEmpty();
        assertThat(data.vocabulariesByFieldCode()).isEmpty();
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
