package fr.siamois.domain.services.form;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
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
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldStratigraphy;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerSelectOneActionCode;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerSelectOneActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerStratigraphy;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectMultiplePerson;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectOnePerson;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectMultiple;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectOneFromFieldCode;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerDateTimeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerIntegerViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
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

    @InjectMocks
    private CustomFieldAnswerService service;

    private FormConfigAnswer formConfigAnswer;

    @BeforeEach
    void setUp() {
        formConfigAnswer = new FormConfigAnswer();
        formConfigAnswer.setId(1L);
    }

    // ========== The entity a field is answered with ==========

    /**
     * One row per field type that has an answer entity, with a value that entity accepts.
     * <p>
     * The seven other field types — measurement, address, and the container / phase / specimen /
     * recording unit selections — have no discriminator in {@code custom_field_answer}; they are
     * covered by {@link #save_shouldFailRatherThanWriteAnAnswerForAFieldTypeThatHasNone()}.
     */
    static Stream<Arguments> fieldTypesAndTheirAnswers() {
        Concept concept = concept(10L);
        Person person = person(20L);
        SpatialUnit spatialUnit = spatialUnit(30L);
        ActionUnit actionUnit = actionUnit(40L);
        ActionCode actionCode = actionCode("FOUILLE");
        StratigraphicRelationshipDTO relationship = new StratigraphicRelationshipDTO();
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
                        actionUnit, CustomFieldAnswerSelectOneActionUnit.class, actionUnit),
                arguments(stratigraphyField(12L),
                        relationship, CustomFieldAnswerStratigraphy.class, relationship)
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
        CustomFieldMeasurement field = CustomFieldMeasurement.builder().id(1L).build();

        CustomFormResponseViewModel viewModel = response(field, viewModelOf("14,2 cm"));
        assertThatThrownBy(() -> service.save(viewModel))
                .isInstanceOf(RuntimeException.class);

        verify(customFieldAnswerRepository, never()).save(any());
    }

    // ========== Helpers ==========

    /**
     * Saves one answer and hands back the entity that reached the repository, which is what the
     * assertions are about: the service returns nothing, it writes.
     */
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

    /**
     * A view model carrying an arbitrary value: the service reads nothing else from it, so this
     * keeps the parameterized tests about the field type → entity mapping. The real view models of
     * the select fields cannot be used there — they hold DTOs, which the entities reject.
     */
    private static CustomFieldAnswerViewModel viewModelOf(Object value) {
        return new CustomFieldAnswerViewModel() {
            @Override
            public Object getValue() {
                return value;
            }
        };
    }

    private static CustomFieldStratigraphy stratigraphyField(Long id) {
        CustomFieldStratigraphy field = new CustomFieldStratigraphy();
        field.setId(id);
        return field;
    }

    private static Concept concept(Long id) {
        Concept concept = new Concept();
        concept.setId(id);
        return concept;
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
}
