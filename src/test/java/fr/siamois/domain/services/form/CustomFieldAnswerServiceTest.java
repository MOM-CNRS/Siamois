package fr.siamois.domain.services.form;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneAddress;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultiple;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerSelectOneActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customfieldanswer.measurement.CustomFieldAnswerMeasurement;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectMultiplePerson;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectOnePerson;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerAnswerSelectMultiple;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerAnswerSelectOne;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectOneFromFieldAnswerCode;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
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
import static org.mockito.ArgumentMatchers.nullable;
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
    private CustomFieldMeasurementService customFieldMeasurementService;
    @Mock
    private UnitDefinitionService unitDefinitionService;
    @Mock
    private UnitDefinitionMapper unitDefinitionMapper;
    @Mock
    private ConceptMapper conceptMapper;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private LabelService labelService;

    @Mock
    private PersonRepository personRepository;
    @Mock
    private fr.siamois.mapper.PersonMapper personMapper;
    @Mock
    private fr.siamois.mapper.SpatialUnitMapper spatialUnitMapper;
    @Mock
    private fr.siamois.mapper.PlaceSuggestionMapper placeSuggestionMapper;
    @Mock
    private fr.siamois.mapper.ActionUnitSummaryMapper actionUnitSummaryMapper;
    @Mock
    private RecordingUnitRepository recordingUnitRepository;
    @Mock
    private fr.siamois.infrastructure.database.repositories.SpatialUnitRepository spatialUnitRepository;
    @Mock
    private fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository actionUnitRepository;

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
        LocalDateTime moment = LocalDateTime.of(2026, Month.JULY, 28, 14, 30);

        return Stream.of(
                arguments(CustomFieldText.builder().id(1L).build(),
                        "Céramique fine", CustomFieldAnswerText.class, "Céramique fine"),
                arguments(CustomFieldInteger.builder().id(2L).build(),
                        42, CustomFieldAnswerInteger.class, 42),
                arguments(CustomFieldDateTime.builder().id(3L).build(),
                        moment, CustomFieldAnswerDateTime.class, moment),
                arguments(CustomFieldSelectOneFromFieldCode.builder().id(4L).build(),
                        concept, CustomFieldAnswerSelectOneFromFieldAnswerCode.class, concept),
                arguments(CustomFieldSelectMultipleFromFieldCode.builder().id(5L).build(),
                        new ArrayList<>(List.of(concept)), CustomFieldAnswerAnswerSelectMultiple.class, List.of(concept)),
                arguments(CustomFieldSelectOnePerson.builder().id(6L).build(),
                        person, CustomFieldAnswerSelectOnePerson.class, person),
                arguments(CustomFieldSelectMultiplePerson.builder().id(7L).build(),
                        List.of(person), CustomFieldAnswerSelectMultiplePerson.class, List.of(person)),
                arguments(CustomFieldSelectOneSpatialUnit.builder().id(8L).build(),
                        spatialUnit, CustomFieldAnswerSelectOneSpatialUnit.class, spatialUnit),
                arguments(CustomFieldSelectMultipleSpatialUnitTree.builder().id(9L).build(),
                        List.of(spatialUnit), CustomFieldAnswerSelectMultipleSpatialUnitTree.class, List.of(spatialUnit)),
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
    void save_shouldSkipAFieldTypeThatHasNoAnswerEntityAndKeepTheOthers() {
        CustomFieldSelectOneAddress address = CustomFieldSelectOneAddress.builder().id(1L).build();
        CustomFieldText text = CustomFieldText.builder().id(2L).build();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(address, viewModelOf("12 rue des Lices"));
        answers.put(text, viewModelOf("Céramique fine"));

        service.save(new CustomFormResponseViewModel(formConfigAnswer, answers));

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository).save(saved.capture());
        assertThat(saved.getValue()).isInstanceOf(CustomFieldAnswerText.class);
    }

    // ========== Additional "vocabulaire contrôlé" fields ==========

    @Test
    void save_shouldStoreTheConceptPickedOnAnAdditionalVocabularyField() {
        CustomFieldSelectOne field = selectOneField(30L);
        Concept concept = concept(10L, "th1");
        when(conceptRepository.findAllById(List.of(10L))).thenReturn(List.of(concept));

        CustomFieldAnswerSelectOneFromFieldCodeViewModel viewModel = new CustomFieldAnswerSelectOneFromFieldCodeViewModel();
        viewModel.setValue(autocompleteDTO(10L, "Fosse"));

        CustomFieldAnswer saved = savedAnswerOf(field, viewModel);

        assertThat(saved).isExactlyInstanceOf(CustomFieldAnswerAnswerSelectOne.class);
        assertThat(saved.getValue()).isSameAs(concept);
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_shouldStoreEveryConceptPickedOnAnAdditionalMultiValueVocabularyField() {
        CustomFieldSelectMultiple field = selectMultipleField(31L);
        Concept fosse = concept(10L, "th1");
        Concept mur = concept(11L, "th2");
        when(conceptRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(fosse, mur));

        CustomFieldAnswerSelectMultipleFromFieldCodeViewModel viewModel = new CustomFieldAnswerSelectMultipleFromFieldCodeViewModel();
        viewModel.setValue(new ArrayList<>(List.of(autocompleteDTO(10L, "Fosse"), autocompleteDTO(11L, "Mur"))));

        CustomFieldAnswer saved = savedAnswerOf(field, viewModel);

        assertThat(saved).isExactlyInstanceOf(CustomFieldAnswerAnswerSelectMultiple.class);
        assertThat((List<Concept>) saved.getValue()).containsExactly(fosse, mur);
    }

    @Test
    void save_shouldNotWriteARowForAVocabularyFieldLeftEmpty() {
        CustomFieldSelectOne field = selectOneField(30L);

        service.save(response(field, new CustomFieldAnswerSelectOneFromFieldCodeViewModel()));

        verify(customFieldAnswerRepository, never()).save(any());
    }

    @Test
    void loadAdditionalFieldAnswers_readsBackTheConceptOfAVocabularyField() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, (Long) null))
                .thenReturn(Optional.of(formConfig));

        CustomFieldSelectOne field = selectOneField(30L);
        Concept concept = concept(10L, "th1");
        CustomFieldAnswerAnswerSelectOne answer = new CustomFieldAnswerAnswerSelectOne();
        answer.setCustomField(field);
        answer.setValue(concept);

        ConceptDTO conceptDTO = conceptDto(10L);
        when(conceptMapper.convert(concept)).thenReturn(conceptDTO);
        when(labelService.findLabelOf(concept, "fr")).thenReturn(prefLabel("Fosse"));

        formConfigAnswer.setAnswers(Set.of(answer));
        when(formConfigAnswerService.findFormConfigAnswer(formConfig, unit)).thenReturn(Optional.of(formConfigAnswer));

        Map<CustomField, CustomFieldAnswerViewModel> result = service.loadAdditionalFieldAnswers(unit);

        assertThat(result.get(field)).isInstanceOfSatisfying(CustomFieldAnswerSelectOneFromFieldCodeViewModel.class, v -> {
            assertThat(v.getValue().concept()).isSameAs(conceptDTO);
            assertThat(v.getValue().getConceptLabelToDisplay().getLabel()).isEqualTo("Fosse");
        });
    }

    @Test
    void loadAdditionalFieldAnswers_readsBackEveryConceptOfAMultiValueVocabularyField() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, (Long) null))
                .thenReturn(Optional.of(formConfig));

        CustomFieldSelectMultiple field = selectMultipleField(31L);
        Concept fosse = concept(10L, "th1");
        Concept mur = concept(11L, "th2");
        CustomFieldAnswerAnswerSelectMultiple answer = new CustomFieldAnswerAnswerSelectMultiple();
        answer.setCustomField(field);
        answer.setValue(new ArrayList<>(List.of(fosse, mur)));

        when(conceptMapper.convert(fosse)).thenReturn(conceptDto(10L));
        when(conceptMapper.convert(mur)).thenReturn(conceptDto(11L));
        when(labelService.findLabelOf(fosse, "fr")).thenReturn(prefLabel("Fosse"));
        when(labelService.findLabelOf(mur, "fr")).thenReturn(prefLabel("Mur"));

        formConfigAnswer.setAnswers(Set.of(answer));
        when(formConfigAnswerService.findFormConfigAnswer(formConfig, unit)).thenReturn(Optional.of(formConfigAnswer));

        Map<CustomField, CustomFieldAnswerViewModel> result = service.loadAdditionalFieldAnswers(unit);

        assertThat(result.get(field)).isInstanceOfSatisfying(CustomFieldAnswerSelectMultipleFromFieldCodeViewModel.class,
                v -> assertThat(v.getValue())
                        .extracting(dto -> dto.getConceptLabelToDisplay().getLabel())
                        .containsExactly("Fosse", "Mur"));
    }

    // ========== saveAdditionalFieldAnswers ==========

    @Test
    void saveAdditionalFieldAnswers_doesNothing_whenAnswersIsNullOrEmpty() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);

        service.saveAdditionalFieldAnswers(unit, null);
        service.saveAdditionalFieldAnswers(unit, new HashMap<>());

        // Only the lookup of former-type answer sets runs; nothing is written.
        verifyNoInteractions(tableFieldConfigService, customFieldAnswerRepository);
        verify(formConfigAnswerService, never()).createOrGetFormConfigAnswer(any(FormConfig.class), any(RecordingUnitDTO.class));
    }

    @Test
    void saveAdditionalFieldAnswers_resolvesDefaultType_whenUnitHasNoType() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        Map<CustomField, CustomFieldAnswerViewModel> answers =
                Map.of(CustomFieldText.builder().id(1L).build(), viewModelOf("valeur"));

        service.saveAdditionalFieldAnswers(unit, answers);

        verify(tableFieldConfigService).getActiveAdditionalFields(7L, ConfigurableTable.UE, (Long) null);
        verify(formConfigAnswerService, never()).createOrGetFormConfigAnswer(any(FormConfig.class), any(RecordingUnitDTO.class));
        verify(customFieldAnswerRepository, never()).save(any());
    }

    @Test
    void saveAdditionalFieldAnswers_resolvesTypeId_whenUnitHasAType() {
        ConceptDTO type = new ConceptDTO();
        type.setId(50L);
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, type);
        Map<CustomField, CustomFieldAnswerViewModel> answers =
                Map.of(CustomFieldText.builder().id(1L).build(), viewModelOf("valeur"));

        service.saveAdditionalFieldAnswers(unit, answers);

        verify(tableFieldConfigService).getActiveAdditionalFields(7L, ConfigurableTable.UE, 50L);
    }

    @Test
    void saveAdditionalFieldAnswers_doesNothing_whenNoFormConfigCanBeResolved() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        CustomField field = CustomFieldText.builder().id(1L).build();
        Map<CustomField, CustomFieldAnswerViewModel> answers = Map.of(field, viewModelOf("x"));
        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.UE, (Long) null))
                .thenReturn(List.of(field));
        when(tableFieldConfigService.createOrGetFormConfig(any(), any(), nullable(Long.class))).thenReturn(Optional.empty());

        service.saveAdditionalFieldAnswers(unit, answers);

        verify(formConfigAnswerService, never()).createOrGetFormConfigAnswer(any(FormConfig.class), any(RecordingUnitDTO.class));
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
        when(tableFieldConfigService.createOrGetFormConfig(7L, ConfigurableTable.UE, (Long) null))
                .thenReturn(Optional.of(formConfig));
        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.UE, (Long) null))
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

        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.UE, (Long) null))
                .thenReturn(List.of());

        service.saveAdditionalFieldAnswers(unit, answers);

        // Not even a form config is resolved: there is nothing to hang an answer on
        verify(tableFieldConfigService, never()).createOrGetFormConfig(any(), any(), nullable(Long.class));
        verify(formConfigAnswerService, never()).createOrGetFormConfigAnswer(any(FormConfig.class), any(RecordingUnitDTO.class));
        verify(customFieldAnswerRepository, never()).save(any());
    }

    @Test
    void saveAdditionalFieldAnswers_persistsThroughTheResolvedFormConfigAnswer() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        CustomFieldText field = CustomFieldText.builder().id(1L).build();
        Map<CustomField, CustomFieldAnswerViewModel> answers = Map.of(field, viewModelOf("Deux tessons"));

        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.createOrGetFormConfig(7L, ConfigurableTable.UE, (Long) null))
                .thenReturn(Optional.of(formConfig));
        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.UE, (Long) null))
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
        when(tableFieldConfigService.createOrGetFormConfig(7L, ConfigurableTable.UE, (Long) null))
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
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, (Long) null))
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
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, (Long) null))
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
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, (Long) null))
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
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, (Long) null))
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
    void loadAdditionalFieldAnswers_givesADateTimeAnswerBackToItsViewModel() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        FormConfig formConfig = new FormConfig();
        formConfig.setId(9L);
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, (Long) null))
                .thenReturn(Optional.of(formConfig));

        CustomFieldDateTime dateField = CustomFieldDateTime.builder().id(3L).build();
        CustomFieldAnswerDateTime dateAnswer = new CustomFieldAnswerDateTime();
        dateAnswer.setCustomField(dateField);
        LocalDateTime moment = LocalDateTime.of(2026, Month.JULY, 29, 10, 0);
        dateAnswer.setValue(moment);

        formConfigAnswer.setAnswers(Set.of(dateAnswer));
        when(formConfigAnswerService.findFormConfigAnswer(formConfig, unit)).thenReturn(Optional.of(formConfigAnswer));

        Map<CustomField, CustomFieldAnswerViewModel> result = service.loadAdditionalFieldAnswers(unit);

        assertThat(result.get(dateField).getValue()).isEqualTo(moment);
    }

    // ========== References given as DTOs, emptied answers ==========

    @Test
    void save_shouldResolveAPersonGivenAsADtoToItsEntity() {
        CustomFieldSelectOnePerson field = CustomFieldSelectOnePerson.builder().id(6L).build();
        PersonDTO picked = new PersonDTO();
        picked.setId(20L);
        Person stored = person(20L);
        when(personRepository.findAllById(List.of(20L))).thenReturn(List.of(stored));

        CustomFieldAnswer saved = savedAnswerOf(field, viewModelOf(picked));

        assertThat(saved.getValue()).isSameAs(stored);
    }

    @Test
    void save_shouldKeepThePickedOrderOfAMultiValueReference() {
        CustomFieldSelectMultiplePerson field = CustomFieldSelectMultiplePerson.builder().id(7L).build();
        PersonDTO first = new PersonDTO();
        first.setId(21L);
        PersonDTO second = new PersonDTO();
        second.setId(20L);
        when(personRepository.findAllById(List.of(21L, 20L))).thenReturn(List.of(person(20L), person(21L)));

        CustomFieldAnswer saved = savedAnswerOf(field, viewModelOf(List.of(first, second)));

        assertThat((List<?>) saved.getValue()).extracting(p -> ((Person) p).getId()).containsExactly(21L, 20L);
    }

    @Test
    void save_shouldDeleteTheStoredAnswerOfAnEmptiedField() {
        CustomFieldText field = CustomFieldText.builder().id(1L).build();
        CustomFieldAnswerText stored = new CustomFieldAnswerText();
        when(customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(formConfigAnswer, field))
                .thenReturn(Optional.of(stored));

        service.save(response(field, viewModelOf("  ")));

        verify(customFieldAnswerRepository).delete(stored);
        verify(customFieldAnswerRepository, never()).save(any());
    }

    @Test
    void save_shouldNotMaterializeAnAnswerForAFieldNeverAnsweredAndLeftEmpty() {
        CustomFieldSelectMultiplePerson field = CustomFieldSelectMultiplePerson.builder().id(7L).build();

        service.save(response(field, viewModelOf(List.of())));

        verify(customFieldAnswerRepository, never()).save(any());
        verify(customFieldAnswerRepository, never()).delete(any());
    }

    // ========== The other answer owners ==========

    @Test
    void saveAdditionalFieldAnswers_ofAPhase_usesThePhaseTableAndItsType() {
        ConceptDTO type = new ConceptDTO();
        type.setId(60L);
        PhaseDTO phase = new PhaseDTO();
        phase.setId(300L);
        phase.setType(type);
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(7L);
        phase.setActionUnit(project);
        CustomFieldText field = CustomFieldText.builder().id(1L).build();
        FormConfig formConfig = new FormConfig();
        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.PHASE, 60L)).thenReturn(List.of(field));
        when(tableFieldConfigService.createOrGetFormConfig(7L, ConfigurableTable.PHASE, 60L)).thenReturn(Optional.of(formConfig));
        when(formConfigAnswerService.createOrGetFormConfigAnswer(formConfig, phase)).thenReturn(formConfigAnswer);

        service.saveAdditionalFieldAnswers(phase, Map.of(field, viewModelOf("Phase ancienne")));

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getFormConfigAnswer()).isSameAs(formConfigAnswer);
        assertThat(saved.getValue().getValue()).isEqualTo("Phase ancienne");
    }

    private static fr.siamois.dto.entity.DocumentDTO documentDto(long id, long projectId, Long typeId) {
        fr.siamois.dto.entity.DocumentDTO document = new fr.siamois.dto.entity.DocumentDTO();
        document.setId(id);
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(projectId);
        document.setActionUnit(project);
        if (typeId != null) document.setType(conceptDto(typeId));
        return document;
    }

    @Test
    void saveAdditionalFieldAnswers_ofADocument_usesTheDocumentTableAndItsCategory() {
        fr.siamois.dto.entity.DocumentDTO document = documentDto(500L, 7L, 80L);
        CustomFieldText field = CustomFieldText.builder().id(1L).build();
        FormConfig formConfig = new FormConfig();
        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.DOCUMENT, 80L)).thenReturn(List.of(field));
        when(tableFieldConfigService.createOrGetFormConfig(7L, ConfigurableTable.DOCUMENT, 80L)).thenReturn(Optional.of(formConfig));
        when(formConfigAnswerService.createOrGetFormConfigAnswer(formConfig, document)).thenReturn(formConfigAnswer);

        service.saveAdditionalFieldAnswers(document, Map.of(field, viewModelOf("Plan")));

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getValue()).isEqualTo("Plan");
    }

    @Test
    void loadAdditionalFieldAnswers_ofADocument_usesItsCategory_andIgnoresOneWithoutId() {
        assertThat(service.loadAdditionalFieldAnswers(documentDto(500L, 7L, 80L))).isEmpty();
        assertThat(service.loadAdditionalFieldAnswers(new fr.siamois.dto.entity.DocumentDTO())).isEmpty();

        verify(tableFieldConfigService).findFormConfig(7L, ConfigurableTable.DOCUMENT, 80L);
    }

    @Test
    void deleteAdditionalFieldAnswers_ofADocument_removesEverySetAndItsAnswers() {
        fr.siamois.dto.entity.DocumentDTO document = documentDto(500L, 7L, 80L);
        FormConfigAnswer set = new FormConfigAnswer();
        CustomFieldAnswerText answer = textAnswer(CustomFieldText.builder().id(1L).build(), set, "x");
        set.setAnswers(new HashSet<>(Set.of(answer)));
        when(formConfigAnswerService.findAllFormConfigAnswers(document)).thenReturn(List.of(set));

        service.deleteAdditionalFieldAnswers(document);

        verify(customFieldAnswerRepository).deleteAll(Set.of(answer));
        verify(formConfigAnswerService).delete(set);
    }

    @Test
    void loadAdditionalFieldAnswers_ofAFindKnownOnlyThroughItsRecordingUnit_resolvesTheProjectFromIt() {
        ConceptDTO category = new ConceptDTO();
        category.setId(70L);
        SpecimenDTO find = new SpecimenDTO();
        find.setId(400L);
        find.setType(category);
        RecordingUnitSummaryDTO unitSummary = new RecordingUnitSummaryDTO();
        unitSummary.setId(100L);
        find.setRecordingUnit(unitSummary);
        RecordingUnit unit = new RecordingUnit();
        unit.setActionUnit(actionUnit(7L));
        when(recordingUnitRepository.findById(100L)).thenReturn(Optional.of(unit));

        service.loadAdditionalFieldAnswers(find);

        // A find's form config is keyed by its category, not its type.
        verify(tableFieldConfigService).findFormConfig(7L, ConfigurableTable.MOBILIER, 70L);
    }

    // ========== Phase / container owners, and a whole page of entities ==========

    @Test
    void loadAdditionalFieldAnswers_ofAPhaseAndAContainer_usesTheirTableAndType() {
        ConceptDTO type = conceptDto(60L);
        PhaseDTO phase = new PhaseDTO();
        phase.setId(300L);
        phase.setType(type);
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(7L);
        phase.setActionUnit(project);
        ContainerDTO container = new ContainerDTO();
        container.setId(301L);
        container.setActionUnit(project);

        assertThat(service.loadAdditionalFieldAnswers(phase)).isEmpty();
        assertThat(service.loadAdditionalFieldAnswers(container)).isEmpty();
        assertThat(service.loadAdditionalFieldAnswers((PhaseDTO) null)).isEmpty();
        assertThat(service.loadAdditionalFieldAnswers(new ContainerDTO())).isEmpty();

        verify(tableFieldConfigService).findFormConfig(7L, ConfigurableTable.PHASE, 60L);
        verify(tableFieldConfigService).findFormConfig(7L, ConfigurableTable.CONTENANT, (Long) null);
    }

    @Test
    void saveAdditionalFieldAnswers_ofAContainerAndAnEntityWithoutProject_resolveTheirOwner() {
        ContainerDTO container = new ContainerDTO();
        container.setId(301L);
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(7L);
        container.setActionUnit(project);
        service.saveAdditionalFieldAnswers(container, Map.of());
        verify(customFieldAnswerRepository, never()).save(any());

        // No project (a find with neither an action unit nor a recording unit): nothing to persist, no failure.
        SpecimenDTO orphan = new SpecimenDTO();
        orphan.setId(9L);
        service.saveAdditionalFieldAnswers(orphan, Map.of(CustomFieldText.builder().id(1L).build(), viewModelOf("x")));
        verify(customFieldAnswerRepository, never()).save(any());
    }

    private static CustomFieldAnswerText textAnswer(CustomField field, FormConfigAnswer set, String value) {
        CustomFieldAnswerText answer = new CustomFieldAnswerText();
        answer.setCustomField(field);
        answer.setFormConfigAnswer(set);
        answer.setValue(value);
        return answer;
    }

    @Test
    void loadAdditionalFieldAnswers_ofAPageOfEntities_groupsByOwnerAndTheMostRecentSetWins() {
        CustomFieldText field = CustomFieldText.builder().id(1L).build();
        for (CustomerOwnerCase c : ownerCases()) {
            FormConfigAnswer older = new FormConfigAnswer();
            older.setId(1L);
            FormConfigAnswer newer = new FormConfigAnswer();
            newer.setId(2L);
            c.attach().accept(older);
            c.attach().accept(newer);
            when(c.query().apply(customFieldAnswerRepository, List.of(5L))).thenReturn(List.of(
                    textAnswer(field, newer, "recent"), textAnswer(field, older, "former")));

            Map<Long, Map<CustomField, CustomFieldAnswerViewModel>> result =
                    service.loadAdditionalFieldAnswers(c.owner(), List.of(5L), List.of(1L));

            assertThat(result).containsOnlyKeys(5L);
            assertThat(result.get(5L).get(field)).isInstanceOfSatisfying(CustomFieldAnswerTextViewModel.class,
                    v -> assertThat(v.getValue()).isEqualTo("recent"));
        }
    }

    @Test
    void loadAdditionalFieldAnswers_ofAPage_withoutOwnersOrFieldsAsksNothing() {
        assertThat(service.loadAdditionalFieldAnswers(CustomFieldAnswerService.ListOwner.PHASE, List.of(), List.of(1L))).isEmpty();
        assertThat(service.loadAdditionalFieldAnswers(CustomFieldAnswerService.ListOwner.PHASE, null, List.of(1L))).isEmpty();
        assertThat(service.loadAdditionalFieldAnswers(CustomFieldAnswerService.ListOwner.PHASE, List.of(1L), List.of())).isEmpty();
        assertThat(service.loadAdditionalFieldAnswers(CustomFieldAnswerService.ListOwner.PHASE, List.of(1L), null)).isEmpty();
        verifyNoInteractions(customFieldAnswerRepository);
    }

    @Test
    void loadAdditionalFieldAnswers_ofAPage_skipsAnswersWhoseOwnerOrTypeIsUnknown() {
        CustomFieldText text = CustomFieldText.builder().id(1L).build();
        FormConfigAnswer ownerless = new FormConfigAnswer();
        ownerless.setId(1L);
        CustomFieldSelectOneAddress address = new CustomFieldSelectOneAddress();
        address.setId(2L);
        FormConfigAnswer owned = new FormConfigAnswer();
        owned.setId(2L);
        owned.setPhase(phaseEntity(5L));
        when(customFieldAnswerRepository.findAnswersOfPhases(List.of(5L), List.of(1L, 2L))).thenReturn(List.of(
                textAnswer(text, ownerless, "no owner"), textAnswer(address, owned, "no view model for this type")));

        assertThat(service.loadAdditionalFieldAnswers(CustomFieldAnswerService.ListOwner.PHASE, List.of(5L), List.of(1L, 2L))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadAdditionalFieldAnswers_readsBackEveryKindOfStoredValue() {
        FormConfigAnswer set = new FormConfigAnswer();
        set.setId(1L);
        set.setPhase(phaseEntity(5L));

        Person person = person(20L);
        SpatialUnit place = spatialUnit(30L);
        ActionUnit project = actionUnit(40L);
        PersonDTO personDto = new PersonDTO();
        SpatialUnitDTO placeDto = new SpatialUnitDTO();
        PlaceSuggestionDTO suggestion = new PlaceSuggestionDTO();
        ActionUnitSummaryDTO projectDto = new ActionUnitSummaryDTO();
        when(personMapper.convert(any(Person.class))).thenReturn(personDto);
        when(spatialUnitMapper.convert(any(SpatialUnit.class))).thenReturn(placeDto);
        when(placeSuggestionMapper.convert(any(SpatialUnitDTO.class))).thenReturn(suggestion);
        when(actionUnitSummaryMapper.convert(any(ActionUnit.class))).thenReturn(projectDto);

        List<CustomFieldAnswer> answers = new ArrayList<>();
        Map<Long, CustomField> fields = new HashMap<>();
        answers.add(withField(fields, 1L, CustomFieldText.builder().id(1L).build(), new CustomFieldAnswerText(), set, "t"));
        answers.add(withField(fields, 2L, CustomFieldDecimal.builder().id(2L).build(), new fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDecimal(), set, 1.5));
        answers.add(withField(fields, 3L, CustomFieldDateTime.builder().id(3L).build(), new CustomFieldAnswerDateTime(), set, LocalDateTime.of(2026, java.time.Month.JANUARY, 2, 3, 4)));
        answers.add(withField(fields, 4L, CustomFieldSelectOnePerson.builder().id(4L).build(), new CustomFieldAnswerSelectOnePerson(), set, person));
        answers.add(withField(fields, 5L, CustomFieldSelectMultiplePerson.builder().id(5L).build(), new CustomFieldAnswerSelectMultiplePerson(), set, new ArrayList<>(List.of(person))));
        answers.add(withField(fields, 6L, CustomFieldSelectOneSpatialUnit.builder().id(6L).build(), new CustomFieldAnswerSelectOneSpatialUnit(), set, place));
        answers.add(withField(fields, 7L, CustomFieldSelectMultipleSpatialUnitTree.builder().id(7L).build(), new CustomFieldAnswerSelectMultipleSpatialUnitTree(), set, new ArrayList<>(List.of(place))));
        answers.add(withField(fields, 8L, CustomFieldSelectOneActionUnit.builder().id(8L).build(), new CustomFieldAnswerSelectOneActionUnit(), set, project));
        when(customFieldAnswerRepository.findAnswersOfPhases(List.of(5L), List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L))).thenReturn(answers);

        Map<CustomField, CustomFieldAnswerViewModel> read = service
                .loadAdditionalFieldAnswers(CustomFieldAnswerService.ListOwner.PHASE, List.of(5L), List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L))
                .get(5L);

        assertThat(read).hasSize(8);
        assertThat(read.get(fields.get(2L)).getValue()).isEqualTo(1.5);
        assertThat(read.get(fields.get(3L)).getValue()).isEqualTo(LocalDateTime.of(2026, java.time.Month.JANUARY, 2, 3, 4));
        assertThat(read.get(fields.get(4L)).getValue()).isSameAs(personDto);
        assertThat((List<Object>) read.get(fields.get(5L)).getValue()).containsExactly(personDto);
        assertThat(read.get(fields.get(6L)).getValue()).isSameAs(suggestion);
        assertThat((List<Object>) read.get(fields.get(7L)).getValue()).containsExactly(suggestion);
        assertThat(read.get(fields.get(8L)).getValue()).isSameAs(projectDto);
    }

    private static CustomFieldAnswer withField(Map<Long, CustomField> fields, long id, CustomField field,
                                               CustomFieldAnswer answer, FormConfigAnswer set, Object value) {
        fields.put(id, field);
        answer.setCustomField(field);
        answer.setFormConfigAnswer(set);
        answer.setValue(value);
        return answer;
    }

    private static fr.siamois.domain.models.phase.Phase phaseEntity(long id) {
        fr.siamois.domain.models.phase.Phase phase = new fr.siamois.domain.models.phase.Phase();
        phase.setId(id);
        return phase;
    }

    private record CustomerOwnerCase(CustomFieldAnswerService.ListOwner owner,
                                     java.util.function.Consumer<FormConfigAnswer> attach,
                                     QueryOf query) {
    }

    @FunctionalInterface
    private interface QueryOf {
        List<CustomFieldAnswer> apply(CustomFieldAnswerRepository repository, Collection<Long> ownerIds);
    }

    private static List<CustomerOwnerCase> ownerCases() {
        RecordingUnit unit = new RecordingUnit();
        unit.setId(5L);
        fr.siamois.domain.models.specimen.Specimen specimen = new fr.siamois.domain.models.specimen.Specimen();
        specimen.setId(5L);
        fr.siamois.domain.models.container.Container container = new fr.siamois.domain.models.container.Container();
        container.setId(5L);
        fr.siamois.domain.models.document.Document document = new fr.siamois.domain.models.document.Document();
        document.setId(5L);
        return List.of(
                new CustomerOwnerCase(CustomFieldAnswerService.ListOwner.DOCUMENT, set -> set.setDocument(document),
                        (repo, ids) -> repo.findAnswersOfDocuments(ids, List.of(1L))),
                new CustomerOwnerCase(CustomFieldAnswerService.ListOwner.RECORDING_UNIT, set -> set.setRecordingUnit(unit),
                        (repo, ids) -> repo.findAnswersOfRecordingUnits(ids, List.of(1L))),
                new CustomerOwnerCase(CustomFieldAnswerService.ListOwner.SPECIMEN, set -> set.setSpecimen(specimen),
                        (repo, ids) -> repo.findAnswersOfSpecimens(ids, List.of(1L))),
                new CustomerOwnerCase(CustomFieldAnswerService.ListOwner.PHASE, set -> set.setPhase(phaseEntity(5L)),
                        (repo, ids) -> repo.findAnswersOfPhases(ids, List.of(1L))),
                new CustomerOwnerCase(CustomFieldAnswerService.ListOwner.CONTAINER, set -> set.setContainer(container),
                        (repo, ids) -> repo.findAnswersOfContainers(ids, List.of(1L))));
    }

    // ========== Type change ==========

    @Test
    void save_afterATypeChange_carriesOverTheCommonFieldsAndDeletesTheFormerTypesAnswers() {
        ConceptDTO newType = new ConceptDTO();
        newType.setId(50L);
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, newType);

        CustomFieldText common = CustomFieldText.builder().id(1L).build();
        CustomFieldText formerOnly = CustomFieldText.builder().id(2L).build();
        FormConfig formerConfig = new FormConfig();
        formerConfig.setId(15L);
        FormConfig currentConfig = new FormConfig();
        currentConfig.setId(29L);

        FormConfigAnswer formerSet = new FormConfigAnswer();
        formerSet.setId(1L);
        formerSet.setFormConfig(formerConfig);
        CustomFieldAnswerText commonAnswer = textAnswer(common, formerSet, "454");
        CustomFieldAnswerText formerOnlyAnswer = textAnswer(formerOnly, formerSet, "perdu");
        formerSet.setAnswers(new HashSet<>(Set.of(commonAnswer, formerOnlyAnswer)));

        FormConfigAnswer currentSet = new FormConfigAnswer();
        currentSet.setId(4L);
        currentSet.setFormConfig(currentConfig);

        when(formConfigAnswerService.findAllFormConfigAnswers(unit)).thenReturn(List.of(formerSet));
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, 50L)).thenReturn(Optional.of(currentConfig));
        when(tableFieldConfigService.getActiveAdditionalFields(7L, ConfigurableTable.UE, 50L)).thenReturn(List.of(common));
        when(formConfigAnswerService.createOrGetFormConfigAnswer(currentConfig, unit)).thenReturn(currentSet);

        service.saveAdditionalFieldAnswers(unit, Map.of());

        ArgumentCaptor<CustomFieldAnswer> saved = ArgumentCaptor.forClass(CustomFieldAnswer.class);
        verify(customFieldAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getCustomField()).isSameAs(common);
        assertThat(saved.getValue().getFormConfigAnswer()).isSameAs(currentSet);
        assertThat(saved.getValue().getValue()).isEqualTo("454");
        verify(customFieldAnswerRepository).deleteAll(Set.of(commonAnswer, formerOnlyAnswer));
        verify(formConfigAnswerService).delete(formerSet);
    }

    @Test
    void save_withoutATypeChange_leavesTheCurrentSetAlone() {
        RecordingUnitDTO unit = recordingUnitDto(100L, 7L, null);
        FormConfig currentConfig = new FormConfig();
        currentConfig.setId(29L);
        FormConfigAnswer currentSet = new FormConfigAnswer();
        currentSet.setId(4L);
        currentSet.setFormConfig(currentConfig);
        when(formConfigAnswerService.findAllFormConfigAnswers(unit)).thenReturn(List.of(currentSet));
        when(tableFieldConfigService.findFormConfig(7L, ConfigurableTable.UE, (Long) null)).thenReturn(Optional.of(currentConfig));

        service.saveAdditionalFieldAnswers(unit, Map.of());

        verify(formConfigAnswerService, never()).delete(any());
        verify(customFieldAnswerRepository, never()).deleteAll(any());
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

    private static CustomFieldSelectOne selectOneField(Long id) {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(id);
        field.setIsSystemField(false);
        return field;
    }

    private static CustomFieldSelectMultiple selectMultipleField(Long id) {
        CustomFieldSelectMultiple field = new CustomFieldSelectMultiple();
        field.setId(id);
        field.setIsSystemField(false);
        return field;
    }

    /**
     * {@code Concept#equals} compares externalId + vocabulary, never the id, so two fixtures that
     * differ only by id are equal — and the second {@code when(...)} on them silently replaces the
     * first. Vocabulary concepts therefore get a distinct external id here.
     */
    private static Concept concept(Long id, String externalId) {
        Concept concept = concept(id);
        concept.setExternalId(externalId);
        return concept;
    }

    private static ConceptDTO conceptDto(long id) {
        ConceptDTO dto = new ConceptDTO();
        dto.setId(id);
        return dto;
    }

    private static ConceptAutocompleteDTO autocompleteDTO(long conceptId, String label) {
        return new ConceptAutocompleteDTO(conceptDto(conceptId), label, "fr");
    }

    private static ConceptPrefLabel prefLabel(String label) {
        ConceptPrefLabel prefLabel = new ConceptPrefLabel();
        prefLabel.setLabel(label);
        prefLabel.setLangCode("fr");
        return prefLabel;
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
