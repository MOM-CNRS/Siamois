package fr.siamois.ui.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectOneFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnabledRulesEngineTest {

    // Concrete classes are used because the Factory maps by .getClass()
    private final CustomFieldSelectOneFromFieldCode fieldA = new CustomFieldSelectOneFromFieldCode();
    private final CustomFieldText fieldB = new CustomFieldText();

    @Mock private CustomField colField;
    @Mock private Condition condition;
    @Mock private ColumnRule rule;
    @Mock private ValueProvider baseValueProvider;
    @Mock private ColumnApplier columnApplier;

    private EnabledRulesEngine engine;

    @BeforeEach
    void setUp() {
        // Setup a rule where 'colField' is enabled based on a condition depending on 'fieldA'
        lenient().when(rule.columnField()).thenReturn(colField);
        lenient().when(rule.enabledWhen()).thenReturn(condition);
        lenient().when(condition.dependsOn()).thenReturn(Set.of(fieldA));

        engine = new EnabledRulesEngine(List.of(rule));
    }

    @Test
    @DisplayName("applyAll should evaluate rules using the base provider")
    void applyAll_ShouldEnableColumn_WhenConditionIsTrue() {
        // Given
        when(condition.test(baseValueProvider)).thenReturn(true);

        // When
        engine.applyAll(baseValueProvider, columnApplier);

        // Then
        verify(columnApplier).setColumnEnabled(colField, true);
    }

    @Test
    @DisplayName("applyAll should disable column if the condition throws an exception")
    void applyAll_ShouldDisableColumn_WhenConditionThrows() {
        // Given
        when(condition.test(baseValueProvider)).thenThrow(new RuntimeException("Logic Error"));

        // When
        engine.applyAll(baseValueProvider, columnApplier);

        // Then
        verify(columnApplier).setColumnEnabled(colField, false);
    }

    @Test
    @DisplayName("onAnswerChange should override the specific field value and trigger re-evaluation")
    void onAnswerChange_ShouldOverrideValueAndReevaluate() {
        // Given
        ConceptAutocompleteDTO newConcept = mock(ConceptAutocompleteDTO.class);
        CustomFieldAnswerSelectOneFromFieldCodeViewModel mockedVm = new CustomFieldAnswerSelectOneFromFieldCodeViewModel();

        try (MockedStatic<CustomFieldAnswerFactory> factoryMock = mockStatic(CustomFieldAnswerFactory.class)) {
            // Mock factory to return our specific VM
            factoryMock.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(fieldA))
                    .thenReturn(mockedVm);

            // Verify that the condition receives a ValueProvider containing our new concept
            when(condition.test(argThat(vp -> {
                var answer = vp.getCurrentAnswer(fieldA);
                return answer == mockedVm && mockedVm.getValue() == newConcept;
            }))).thenReturn(true);

            // When
            engine.onAnswerChange(fieldA, newConcept, baseValueProvider, columnApplier);

            // Then
            verify(columnApplier).setColumnEnabled(colField, true);
        }
    }

    @Test
    @DisplayName("onAnswerChange should ignore fields that have no dependents without crashing")
    void onAnswerChange_ShouldDoNothing_WhenFieldIsNotInDependencies() {
        // Given
        ConceptAutocompleteDTO newConcept = mock(ConceptAutocompleteDTO.class);

        // We need a ViewModel that passes the 'instanceof' check in buildConceptOverride
        CustomFieldAnswerSelectOneFromFieldCodeViewModel compatibleVm =
                new CustomFieldAnswerSelectOneFromFieldCodeViewModel();

        try (MockedStatic<CustomFieldAnswerFactory> factoryMock = mockStatic(CustomFieldAnswerFactory.class)) {
            // We must mock the factory because buildConceptOverride is called BEFORE the dependency check
            factoryMock.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(fieldB))
                    .thenReturn(compatibleVm);

            // When: We change fieldB, which is NOT in the engine's dependency index
            engine.onAnswerChange(fieldB, newConcept, baseValueProvider, columnApplier);

            // Then: The engine identifies no impacted columns and exits
            verifyNoInteractions(columnApplier);
        }
    }

    @Test
    @DisplayName("onAnswerChange should throw exception if the field type is incompatible with ConceptAutocompleteDTO")
    void onAnswerChange_ShouldThrowException_WhenFieldCannotHandleConcept() {
        // Given
        ConceptAutocompleteDTO newConcept = mock(ConceptAutocompleteDTO.class);
        // CustomFieldText expects a String, not a Concept, so buildConceptOverride should fail
        CustomFieldAnswerTextViewModel textVm = new CustomFieldAnswerTextViewModel();

        try (MockedStatic<CustomFieldAnswerFactory> factoryMock = mockStatic(CustomFieldAnswerFactory.class)) {
            factoryMock.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(fieldB))
                    .thenReturn(textVm);

            // Setup a rule for fieldB so the engine actually tries to process it
            ColumnRule ruleB = mock(ColumnRule.class);
            Condition condB = mock(Condition.class);
            when(ruleB.columnField()).thenReturn(mock(CustomField.class));
            when(ruleB.enabledWhen()).thenReturn(condB);
            when(condB.dependsOn()).thenReturn(Set.of(fieldB));

            EnabledRulesEngine engineWithB = new EnabledRulesEngine(List.of(ruleB));

            // When / Then
            assertThrows(IllegalArgumentException.class, () ->
                    engineWithB.onAnswerChange(fieldB, newConcept, baseValueProvider, columnApplier)
            );
        }
    }
}