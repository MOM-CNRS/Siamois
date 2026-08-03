package fr.siamois.domain.services.form;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionCode;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneAddress;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerSelectOneActionCode;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerSelectOneActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customfieldanswer.measurement.CustomFieldAnswerMeasurement;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectMultiplePerson;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectOnePerson;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectMultiple;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectOneFromFieldCode;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.mapper.UnitDefinitionMapper;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.*;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldAnswerServiceTest {

    @Mock
    private CustomFieldAnswerRepository customFieldAnswerRepository;
    @Mock
    private TableFieldConfigService tableFieldConfigService;
    @Mock
    private FormConfigAnswerService formConfigAnswerService;
    @Mock
    private LabelService labelService;
    @Mock
    private CustomFieldMeasurementService customFieldMeasurementService;
    @Mock
    private UnitDefinitionService unitDefinitionService;
    @Mock
    private UnitDefinitionMapper unitDefinitionMapper;

    @InjectMocks
    private CustomFieldAnswerService service;

    private FormConfigAnswer formConfigAnswer;

    @BeforeEach
    void setUp() {
        formConfigAnswer = new FormConfigAnswer();
        formConfigAnswer.setId(1L);

        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        PersonDTO person = new PersonDTO();
        person.setId(2L);
        ExecutionContextHolder.set(new UserInfo(institution, person, "fr"));
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    // ========== The entity a field is answered with ==========

    /**
     * One row per field type that has an answer entity, with a value that entity accepts.
     * <p>
     * Measurement fields answer through their own view model rather than an arbitrary value, so
     * they are covered separately by {@link #save_shouldStoreWhatAMeasurementViewModelHolds()}.
     * The six remaining field types — address, and the container / phase / specimen / recording unit
     * selections — have no discriminator in {@code custom_field_answer}; they are covered by
     * {@link #save_shouldFailRatherThanWriteAnAnswerForAFieldTypeThatHasNone()}.
     */
    static Stream<Arguments> fieldTypesAndTheirAnswers() {
        Concept concept = concept(10L);
        Person person = person(20L);
        SpatialUnit spatialUnit = spatialUnit(30L);
        ActionUnit actionUnit = actionUnit(40L);
        ActionCode actionCode = actionCode("FOUILLE");
        LocalDateTime moment = LocalDateTime.of(2026, Month.JULY, 28, 14, 30);

        return Stream.of(
                arguments(CustomFieldText.builder().id(1L).build(),
                        "Céramique fine", CustomFieldAnswerText.class, "Céramique fine"),
                arguments(CustomFieldInteger.builder().id(2L).build(),
                        42, CustomFieldAnswerInteger.class, 42),
                arguments(CustomFieldDateTime.builder().id(3L).build(),
                        moment, CustomFieldAnswerDateTime.class, moment),
                arguments(CustomFieldSelectOneFromFieldCode.builder().id(4L).build(),
                        concept, CustomFieldAnswerSelectOneFromFieldCode.class, concept),
                arguments(CustomFieldSelectMultipleFromFieldCode.builder().id(5L).build(),
                        new ArrayList<>(List.of(concept)), CustomFieldAnswerSelectMultiple.class, List.of(concept)),
                arguments(CustomFieldSelectOnePerson.builder().id(6L).build(),
                        person, CustomFieldAnswerSelectOnePerson.class, person),
                arguments(CustomFieldSelectMultiplePerson.builder().id(7L).build(),
                        List.of(person), CustomFieldAnswerSelectMultiplePerson.class, List.of(person)),
                arguments(CustomFieldSelectOneSpatialUnit.builder().id(8L).build(),
                        spatialUnit, CustomFieldAnswerSelectOneSpatialUnit.class, spatialUnit),
                arguments(CustomFieldSelectMultipleSpatialUnitTree.builder().id(9L).build(),
                        List.of(spatialUnit), CustomFieldAnswerSelectMultipleSpatialUnitTree.class, List.of(spatialUnit)),
                arguments(CustomFieldSelectOneActionCode.builder().id(10L).build(),
                        actionCode, CustomFieldAnswerSelectOneActionCode.class, actionCode),
                arguments(CustomFieldSelectOneActionUnit.builder().id(11L).build(),
                        actionUnit, CustomFieldAnswerSelectOneActionUnit.class, actionUnit)
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("fieldTypesAndTheirAnswers")
    void save_shouldAnswerAFieldWithTheEntityOfItsTypeAndKeepItsValue(CustomField field, Object value,
                                                                     Class<? extends CustomFieldAnswer> expectedType,
                                                                     Object expectedValue) {
        CustomFieldAnswer saved = savedAnswerOf(field, viewModelOf(value));

        assertThat(saved).isExactlyInstanceOf(expectedType);
        assertThat(saved.getValue()).isEqualTo(expectedValue);
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("fieldTypesAndTheirAnswers")
    void save_shouldTieANewAnswerToItsFieldAndResponse(CustomField field, Object value,
                                                       Class<? extends CustomFieldAnswer> expectedType,
                                                       Object expectedValue) {
        CustomFieldAnswer saved = savedAnswerOf(field, viewModelOf(value));

        assertThat(saved.getCustomField()).isSameAs(field);
        assertThat(saved.getFormConfigAnswer()).isSameAs(formConfigAnswer);
    }

    @Test
    void save_shouldGiveEachResponseItsOwnAnswerInstance() {
        CustomFieldText field = CustomFieldText.builder().id(1L).build();

        service.save(response(field, viewModelOf("Première réponse")));
        service.save(response(field, viewModelOf("Seconde réponse")));

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0)).isNotSameAs(saved.getAllValues().get(1));
        assertThat(saved.getAllValues()).extracting(CustomFieldAnswer::getValue)
                .containsExactly("Première réponse", "Seconde réponse");
    }

    // ========== What the real view models carry ==========

    @Test
    void save_shouldStoreWhatATextViewModelHolds() {
        CustomFieldAnswerTextViewModel viewModel = new CustomFieldAnswerTextViewModel();
        viewModel.setValue("Fragment de panse");

        CustomFieldAnswer saved = savedAnswerOf(CustomFieldText.builder().id(1L).build(), viewModel);

        assertThat(saved).isInstanceOf(CustomFieldAnswerText.class);
        assertThat(saved.getValue()).isEqualTo("Fragment de panse");
    }

    @Test
    void save_shouldStoreWhatAnIntegerViewModelHolds() {
        CustomFieldAnswerIntegerViewModel viewModel = new CustomFieldAnswerIntegerViewModel();
        viewModel.setValue(7);

        CustomFieldAnswer saved = savedAnswerOf(CustomFieldInteger.builder().id(2L).build(), viewModel);

        assertThat(saved).isInstanceOf(CustomFieldAnswerInteger.class);
        assertThat(saved.getValue()).isEqualTo(7);
    }

    @Test
    void save_shouldStoreWhatADateTimeViewModelHolds() {
        LocalDateTime moment = LocalDateTime.of(2026, Month.JULY, 28, 9, 15);
        CustomFieldAnswerDateTimeViewModel viewModel = new CustomFieldAnswerDateTimeViewModel();
        viewModel.setValue(moment);

        CustomFieldAnswer saved = savedAnswerOf(CustomFieldDateTime.builder().id(3L).build(), viewModel);

        assertThat(saved).isInstanceOf(CustomFieldAnswerDateTime.class);
        assertThat(saved.getValue()).isEqualTo(moment);
    }

    @Test
    void save_shouldStoreWhatAMeasurementViewModelHolds() {
        CustomFieldAnswerMeasurementViewModel viewModel = new CustomFieldAnswerMeasurementViewModel();
        viewModel.setValue(MeasurementAnswerDTO.builder().numericValue(14.2).comment("au nord").build());

        CustomFieldAnswer saved = savedAnswerOf(CustomFieldMeasurement.builder().id(1L).build(), viewModel);

        assertThat(saved).isInstanceOf(CustomFieldAnswerMeasurement.class);
        assertThat(saved.getValue()).isEqualTo(14.2);
        assertThat(((CustomFieldAnswerMeasurement) saved).getComment()).isEqualTo("au nord");
    }

    @Test
    void save_shouldStoreTheUnitTheMeasurementWasEnteredIn() {
        UnitDefinition metre = unitDefinition(5L);
        when(unitDefinitionService.resolveById(5L)).thenReturn(metre);

        CustomFieldAnswerMeasurementViewModel viewModel = new CustomFieldAnswerMeasurementViewModel();
        viewModel.setValue(MeasurementAnswerDTO.builder()
                .numericValue(14.2)
                .unit(UnitDefinitionDTO.builder().id(5L).build())
                .build());

        CustomFieldAnswer saved = savedAnswerOf(CustomFieldMeasurement.builder().id(1L).build(), viewModel);

        assertThat(((CustomFieldAnswerMeasurement) saved).getUnit()).isSameAs(metre);
    }

    /**
     * The unit only reaches the view model through the field definition anyway, but an answer
     * built elsewhere (the API) may carry none, and it still has to be stored with one.
     */
    @Test
    void save_shouldFallBackToTheFieldsUnitWhenTheAnswerCarriesNone() {
        UnitDefinition metre = unitDefinition(5L);
        when(unitDefinitionService.resolveById(5L)).thenReturn(metre);

        CustomFieldAnswerMeasurementViewModel viewModel = new CustomFieldAnswerMeasurementViewModel();
        viewModel.setValue(MeasurementAnswerDTO.builder().numericValue(14.2).build());

        CustomFieldAnswer saved = savedAnswerOf(
                CustomFieldMeasurement.builder().id(1L).unit(unitDefinition(5L)).build(), viewModel);

        assertThat(((CustomFieldAnswerMeasurement) saved).getUnit()).isSameAs(metre);
    }

    @Test
    void save_shouldFailRatherThanStoreAMeasurementWhoseUnitNoLongerExists() {
        when(unitDefinitionService.resolveById(5L))
                .thenThrow(new IllegalStateException("UnitDefinition not found: 5"));

        CustomFieldAnswerMeasurementViewModel viewModel = new CustomFieldAnswerMeasurementViewModel();
        viewModel.setValue(MeasurementAnswerDTO.builder()
                .numericValue(14.2)
                .unit(UnitDefinitionDTO.builder().id(5L).build())
                .build());

        CustomFormResponseViewModel responseViewModel = response(CustomFieldMeasurement.builder().id(1L).build(), viewModel);
        assertThatThrownBy(() -> service.save(responseViewModel))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("5");
        verify(customFieldAnswerRepository, never()).save(any());
    }

    @Test
    void save_shouldWriteNothingForAMeasurementNobodyFilledIn() {
        CustomFieldAnswerMeasurementViewModel viewModel = new CustomFieldAnswerMeasurementViewModel();
        viewModel.setValue(new MeasurementAnswerDTO());

        service.save(response(CustomFieldMeasurement.builder().id(1L).build(), viewModel));

        verify(customFieldAnswerRepository, never()).save(any());
    }

    @Test
    void save_shouldClearAStoredMeasurementTheUserEmptied() {
        CustomFieldMeasurement field = CustomFieldMeasurement.builder().id(1L).build();
        CustomFieldAnswerMeasurement existing = new CustomFieldAnswerMeasurement();
        existing.setCustomField(field);
        existing.setFormConfigAnswer(formConfigAnswer);
        existing.setValue(14.2);
        when(customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(formConfigAnswer, field))
                .thenReturn(Optional.of(existing));

        CustomFieldAnswerMeasurementViewModel viewModel = new CustomFieldAnswerMeasurementViewModel();
        viewModel.setValue(new MeasurementAnswerDTO());

        CustomFieldAnswer saved = savedAnswerOf(field, viewModel);

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getValue()).isNull();
    }

    // ========== Creating vs updating ==========

    @Test
    void save_shouldUpdateTheAnswerAlreadyStoredRatherThanCreatingASecondOne() {
        CustomFieldText field = CustomFieldText.builder().id(1L).build();
        CustomFieldAnswerText existing = new CustomFieldAnswerText();
        existing.setCustomField(field);
        existing.setFormConfigAnswer(formConfigAnswer);
        existing.setValue("Ancienne description");
        when(customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(formConfigAnswer, field))
                .thenReturn(Optional.of(existing));

        CustomFieldAnswer saved = savedAnswerOf(field, viewModelOf("Nouvelle description"));

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getValue()).isEqualTo("Nouvelle description");
    }

    @Test
    void save_shouldSaveOneAnswerPerFieldOfTheResponse() {
        CustomFieldText textField = CustomFieldText.builder().id(1L).concept(concept(10L)).build();
        CustomFieldInteger integerField = CustomFieldInteger.builder().id(2L).concept(concept(11L)).build();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(textField, viewModelOf("Deux tessons"));
        answers.put(integerField, viewModelOf(2));

        service.save(new CustomFormResponseViewModel(formConfigAnswer, answers));

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).anySatisfy(answer -> {
            assertThat(answer).isInstanceOf(CustomFieldAnswerText.class);
            assertThat(answer.getValue()).isEqualTo("Deux tessons");
        });
        assertThat(saved.getAllValues()).anySatisfy(answer -> {
            assertThat(answer).isInstanceOf(CustomFieldAnswerInteger.class);
            assertThat(answer.getValue()).isEqualTo(2);
        });
    }

    @Test
    void save_shouldWriteNothingForAResponseWithoutAnswer() {
        service.save(new CustomFormResponseViewModel(formConfigAnswer, new HashMap<>()));

        verify(customFieldAnswerRepository, never()).save(any());
    }

    // ========== Field types no answer entity exists for ==========

    @Test
    void save_shouldFailRatherThanWriteAnAnswerForAFieldTypeThatHasNone() {
        CustomFieldSelectOneAddress field = CustomFieldSelectOneAddress.builder().id(1L).build();

        CustomFormResponseViewModel viewModel = response(field, viewModelOf("12 rue des Lices"));
        assertThatThrownBy(() -> service.save(viewModel))
                .isInstanceOf(RuntimeException.class);

        verify(customFieldAnswerRepository, never()).save(any());
    }

    // ========== saveAdditionalFieldAnswers ==========

    @Test
    void saveAdditionalFieldAnswers_doesNothing_whenAnswersIsNullOrEmpty() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);

        service.saveAdditionalFieldAnswers(unit, null);
        service.saveAdditionalFieldAnswers(unit, new HashMap<>());

        verifyNoInteractions(tableFieldConfigService, formConfigAnswerService, customFieldAnswerRepository);
    }

    @Test
    void saveAdditionalFieldAnswers_resolvesDefaultType_whenUnitHasNoType() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        Map<CustomField, CustomFieldAnswerViewModel> answers =
                Map.of(CustomFieldText.builder().id(1L).build(), viewModelOf("valeur"));

        service.saveAdditionalFieldAnswers(unit, answers);

        verify(tableFieldConfigService).getActiveAdditionalFields(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE);
        verifyNoInteractions(formConfigAnswerService);
        verify(customFieldAnswerRepository, never()).save(any());
    }

    @Test
    void saveAdditionalFieldAnswers_resolvesTypeLabel_whenUnitHasAType() {
        ConceptDTO type = new ConceptDTO();
        type.setId(50L);
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, type);
        Map<CustomField, CustomFieldAnswerViewModel> answers =
                Map.of(CustomFieldText.builder().id(1L).build(), viewModelOf("valeur"));

        ConceptPrefLabelDTO label = new ConceptPrefLabelDTO();
        label.setLabel("Céramique");
        when(labelService.findLabelOf(type, "fr")).thenReturn(label);

        service.saveAdditionalFieldAnswers(unit, answers);

        verify(tableFieldConfigService).getActiveAdditionalFields(7L, ConfigurableTable.UE, "Céramique");
    }

    @Test
    void saveAdditionalFieldAnswers_doesNothing_whenNoFormConfigCanBeResolved() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        CustomField field = CustomFieldText.builder().id(1L).build();
        Map<CustomField, CustomFieldAnswerViewModel> answers = Map.of(field, viewModelOf("x"));
        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(List.of(field));
        when(tableFieldConfigService.createOrGetFormConfig(any(), any(), any())).thenReturn(Optional.empty());

        service.saveAdditionalFieldAnswers(unit, answers);

        verifyNoInteractions(formConfigAnswerService);
        verify(customFieldAnswerRepository, never()).save(any());
    }

    @Test
    void saveAdditionalFieldAnswers_filtersOutFieldsNotCurrentlyActive() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        CustomField activeField = CustomFieldText.builder().id(1L).build();
        CustomField inactiveField = CustomFieldText.builder().id(2L).build();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(activeField, viewModelOf("garde"));
        answers.put(inactiveField, viewModelOf("filtre"));

        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.createOrGetFormConfig(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(Optional.of(formConfig));
        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(List.of(activeField));
        when(formConfigAnswerService.createOrGetFormConfigAnswer(formConfig, unit)).thenReturn(formConfigAnswer);

        service.saveAdditionalFieldAnswers(unit, answers);

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getCustomField()).isSameAs(activeField);
    }

    @Test
    void saveAdditionalFieldAnswers_doesNothing_whenEveryAnswerIsFilteredOut() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        CustomField inactiveField = CustomFieldText.builder().id(2L).build();
        Map<CustomField, CustomFieldAnswerViewModel> answers = Map.of(inactiveField, viewModelOf("filtre"));

        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(List.of());

        service.saveAdditionalFieldAnswers(unit, answers);

        // Not even a form config is resolved: there is nothing to hang an answer on
        verify(tableFieldConfigService, never()).createOrGetFormConfig(any(), any(), any());
        verifyNoInteractions(formConfigAnswerService);
        verify(customFieldAnswerRepository, never()).save(any());
    }

    @Test
    void saveAdditionalFieldAnswers_persistsThroughTheResolvedFormConfigAnswer() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        CustomFieldText field = CustomFieldText.builder().id(1L).build();
        Map<CustomField, CustomFieldAnswerViewModel> answers = Map.of(field, viewModelOf("Deux tessons"));

        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.createOrGetFormConfig(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(Optional.of(formConfig));
        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(List.of(field));
        when(formConfigAnswerService.createOrGetFormConfigAnswer(formConfig, unit)).thenReturn(formConfigAnswer);

        service.saveAdditionalFieldAnswers(unit, answers);

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getFormConfigAnswer()).isSameAs(formConfigAnswer);
        assertThat(saved.getValue().getValue()).isEqualTo("Deux tessons");
    }

    @Test
    void saveAdditionalFieldAnswers_keepsAMeasurementFieldCreatedFromTheUnitsOwnForm() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        CustomFieldMeasurement field = CustomFieldMeasurement.builder().id(1L).build();
        CustomFieldAnswerMeasurementViewModel viewModel = new CustomFieldAnswerMeasurementViewModel();
        viewModel.setValue(MeasurementAnswerDTO.builder().numericValue(14.2).build());

        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        // Not part of the project-level configuration, only of the unit's own fields
        when(customFieldMeasurementService.findByRecordingUnit(100L)).thenReturn(List.of(field));
        when(tableFieldConfigService.createOrGetFormConfig(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(Optional.of(formConfig));
        when(formConfigAnswerService.createOrGetFormConfigAnswer(formConfig, unit)).thenReturn(formConfigAnswer);

        service.saveAdditionalFieldAnswers(unit, Map.of(field, viewModel));

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getCustomField()).isSameAs(field);
        assertThat(saved.getValue().getValue()).isEqualTo(14.2);
    }

    // ========== loadAdditionalFieldAnswers ==========

    @Test
    void loadAdditionalFieldAnswers_returnsEmptyMap_whenUnitHasNoId() {
        RecordingUnitDTO unit = recordingUnitDto(null, 7L, null);

        Map<CustomField, CustomFieldAnswerViewModel> result = service.loadAdditionalFieldAnswers(unit);

        assertThat(result).isEmpty();
        verifyNoInteractions(tableFieldConfigService, formConfigAnswerService);
    }

    @Test
    void loadAdditionalFieldAnswers_returnsEmptyMap_whenNoFormConfigFound() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(Optional.empty());

        Map<CustomField, CustomFieldAnswerViewModel> result = service.loadAdditionalFieldAnswers(unit);

        assertThat(result).isEmpty();
        verifyNoInteractions(formConfigAnswerService);
    }

    @Test
    void loadAdditionalFieldAnswers_returnsEmptyMap_whenNoFormConfigAnswerExistsYet() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(Optional.of(formConfig));
        when(formConfigAnswerService.findFormConfigAnswer(formConfig, unit)).thenReturn(Optional.empty());

        Map<CustomField, CustomFieldAnswerViewModel> result = service.loadAdditionalFieldAnswers(unit);

        assertThat(result).isEmpty();
    }

    @Test
    void loadAdditionalFieldAnswers_returnsViewModelsKeyedByField() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(Optional.of(formConfig));

        CustomFieldText textField = CustomFieldText.builder().id(1L).build();
        CustomFieldAnswerText textAnswer = new CustomFieldAnswerText();
        textAnswer.setCustomField(textField);
        textAnswer.setValue("Deux tessons");

        CustomFieldInteger integerField = CustomFieldInteger.builder().id(2L).build();
        CustomFieldAnswerInteger integerAnswer = new CustomFieldAnswerInteger();
        integerAnswer.setCustomField(integerField);
        integerAnswer.setValue(2);

        formConfigAnswer.setAnswers(Set.of(textAnswer, integerAnswer));
        when(formConfigAnswerService.findFormConfigAnswer(formConfig, unit)).thenReturn(Optional.of(formConfigAnswer));

        Map<CustomField, CustomFieldAnswerViewModel> result = service.loadAdditionalFieldAnswers(unit);

        assertThat(result).hasSize(2);
        assertThat(result.get(textField)).isInstanceOfSatisfying(CustomFieldAnswerTextViewModel.class,
                v -> assertThat(v.getValue()).isEqualTo("Deux tessons"));
        assertThat(result.get(integerField)).isInstanceOfSatisfying(CustomFieldAnswerIntegerViewModel.class,
                v -> assertThat(v.getValue()).isEqualTo(2));
        assertThat(result.values()).allSatisfy(v -> assertThat(v.getHasBeenModified()).isFalse());
    }

    @Test
    void loadAdditionalFieldAnswers_readsBackAMeasurementNumberCommentAndUnit() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(Optional.of(formConfig));

        UnitDefinition metre = unitDefinition(5L);
        UnitDefinitionDTO metreDto = UnitDefinitionDTO.builder().id(5L).build();
        when(unitDefinitionMapper.convert(metre)).thenReturn(metreDto);

        CustomFieldMeasurement field = CustomFieldMeasurement.builder().id(1L).build();
        CustomFieldAnswerMeasurement answer = new CustomFieldAnswerMeasurement();
        answer.setCustomField(field);
        answer.setValue(14.2);
        answer.setComment("au nord");
        answer.setUnit(metre);

        formConfigAnswer.setAnswers(Set.of(answer));
        when(formConfigAnswerService.findFormConfigAnswer(formConfig, unit)).thenReturn(Optional.of(formConfigAnswer));

        Map<CustomField, CustomFieldAnswerViewModel> result = service.loadAdditionalFieldAnswers(unit);

        assertThat(result.get(field)).isInstanceOfSatisfying(CustomFieldAnswerMeasurementViewModel.class, v -> {
            assertThat(v.getValue().getNumericValue()).isEqualTo(14.2);
            assertThat(v.getValue().getComment()).isEqualTo("au nord");
            assertThat(v.getValue().getUnit()).isSameAs(metreDto);
        });
    }

    @Test
    void loadAdditionalFieldAnswers_ignoresAnAnswerOfATypeWithNoSupportedViewModelConversion() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, TableFieldConfigService.DEFAULT_TYPE))
                .thenReturn(Optional.of(formConfig));

        // DateTime is persistable (in ANSWER_ENTITY_CREATORS) but its stored value type
        // (LocalDateTime) doesn't match what a view model expects, so it's skipped rather than
        // risking a wrong cast.
        CustomFieldDateTime dateField = CustomFieldDateTime.builder().id(3L).build();
        CustomFieldAnswerDateTime dateAnswer = new CustomFieldAnswerDateTime();
        dateAnswer.setCustomField(dateField);
        dateAnswer.setValue(LocalDateTime.of(2026, Month.JULY, 29, 10, 0));

        formConfigAnswer.setAnswers(Set.of(dateAnswer));
        when(formConfigAnswerService.findFormConfigAnswer(formConfig, unit)).thenReturn(Optional.of(formConfigAnswer));

        Map<CustomField, CustomFieldAnswerViewModel> result = service.loadAdditionalFieldAnswers(unit);

        assertThat(result).isEmpty();
    }

    // ========== Helpers ==========

    private CustomFieldAnswer savedAnswerOf(CustomField field, CustomFieldAnswerViewModel viewModel) {
        service.save(response(field, viewModel));

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository).save(saved.capture());
        return saved.getValue();
    }

    private CustomFormResponseViewModel response(CustomField field, CustomFieldAnswerViewModel viewModel) {
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(field, viewModel);
        return new CustomFormResponseViewModel(formConfigAnswer, answers);
    }

    private static CustomFieldAnswerViewModel viewModelOf(Object value) {
        return new CustomFieldAnswerViewModel() {
            @Override
            public Object getValue() {
                return value;
            }
        };
    }

    private static Concept concept(Long id) {
        Concept concept = new Concept();
        concept.setId(id);
        return concept;
    }

    private static UnitDefinition unitDefinition(Long id) {
        UnitDefinition unitDefinition = new UnitDefinition();
        unitDefinition.setId(id);
        return unitDefinition;
    }

    private static Person person(Long id) {
        Person person = new Person();
        person.setId(id);
        return person;
    }

    private static SpatialUnit spatialUnit(Long id) {
        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setId(id);
        return spatialUnit;
    }

    private static ActionUnit actionUnit(Long id) {
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(id);
        return actionUnit;
    }

    private static ActionCode actionCode(String code) {
        ActionCode actionCode = new ActionCode();
        actionCode.setCode(code);
        return actionCode;
    }

    private static RecordingUnitDTO recordingUnitDto(Long id, Long actionUnitId, ConceptDTO type) {
        RecordingUnitDTO unit = new RecordingUnitDTO();
        unit.setId(id);
        ActionUnitSummaryDTO actionUnit = new ActionUnitSummaryDTO();
        actionUnit.setId(actionUnitId);
        unit.setActionUnit(actionUnit);
        unit.setType(type);
        return unit;
    }
}
