package fr.siamois.ui.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerId;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.VocabularyDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectOneFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EnabledRulesEngineTest {

    // --- helpers: fields/answers/concepts ---

    private static Concept concept(String vocabExtId, String conceptExtId) {
        Vocabulary voc = new Vocabulary();
        voc.setBaseUri("http://example.com");
        voc.setExternalVocabularyId(vocabExtId);
        Concept c = new Concept();
        c.setExternalId(conceptExtId);
        c.setVocabulary(voc);
        return c;
    }

    private static ConceptAutocompleteDTO conceptDTO(String vocabExtId, String conceptExtId) {

        VocabularyDTO voc = new VocabularyDTO();
        voc.setBaseUri("http://example.com");
        voc.setExternalVocabularyId(vocabExtId);
        ConceptDTO c = new ConceptDTO();
        c.setExternalId(conceptExtId);
        c.setVocabulary(voc);
        return  new ConceptAutocompleteDTO(c,"label","fr");
    }

    private static CustomFieldSelectOneFromFieldCode conceptField(long id, String label, Concept concept) {
        CustomFieldSelectOneFromFieldCode f = new CustomFieldSelectOneFromFieldCode();
        f.setId(id);
        f.setConcept(concept);
        f.setLabel(label);
        return f;
    }

    private static CustomFieldText anyColumnField(long id, String label, Concept concept) {
        CustomFieldText f = new CustomFieldText();
        f.setConcept(concept);
        f.setId(id);
        f.setLabel(label);
        return f;
    }

    private static CustomFieldAnswerSelectOneFromFieldCodeViewModel answerFor(CustomField field, ConceptAutocompleteDTO value) {
        CustomFieldAnswerSelectOneFromFieldCodeViewModel a = new CustomFieldAnswerSelectOneFromFieldCodeViewModel();
        CustomFieldAnswerId pk = new CustomFieldAnswerId();
        pk.setField(field);
        a.setPk(pk);
        a.setValue(value);
        return a;
    }

    // --- minimal ValueProvider backed by a map ---
    private static final class MapValueProvider implements ValueProvider {
        private final Map<CustomField, CustomFieldAnswerViewModel> store;
        MapValueProvider(Map<CustomField, CustomFieldAnswerViewModel> store) { this.store = store; }
        @Override public CustomFieldAnswerViewModel getCurrentAnswer(CustomField field) {
            return store.get(field);
        }
    }

    // --- ColumnApplier that records enabled state by field id ---
    private static final class RecordingApplier implements ColumnApplier {
        final Map<Long, Boolean> states = new HashMap<>();
        @Override public void setColumnEnabled(CustomField columnField, boolean enabled) {
            states.put(columnField.getId(), enabled);
        }
        boolean enabled(CustomField f) { return states.getOrDefault(f.getId(), true); }
    }

    private CustomField driverField;     // the field whose value is compared (Concept-valued)
    private CustomField colA;            // a column controlled by a rule on driverField
    private CustomField colB;             // another column controlled by same driverField

    private Concept driverConcept;
    private Concept conceptA;                 // expected concept for "enabled"
    private Concept conceptB;
    private ConceptAutocompleteDTO conceptAdto;                 // expected concept for "enabled"
    private ConceptAutocompleteDTO conceptBdto;   // another concept

    @BeforeEach
    void setUp() {
        driverConcept = concept("th", "driver");
        driverField = conceptField(10L, "Driver Field", driverConcept);
        conceptA = concept("th", "A");
        conceptB = concept("th", "B");
        conceptAdto = conceptDTO("th", "A");
        conceptBdto = conceptDTO("th", "B");
        colA = anyColumnField(100L, "Column A", conceptA);
        colB = anyColumnField(200L, "Column B", conceptB);
    }

    // Helper: a Condition that checks driverField's concept equals an expected concept
    private Condition conceptEquals(CustomField observedField, Concept expected) {
        return new Condition() {
            @Override
            public boolean test(ValueProvider vp) {
                CustomFieldAnswerViewModel ans = vp.getCurrentAnswer(observedField);
                if (!(ans instanceof CustomFieldAnswerSelectOneFromFieldCodeViewModel a)) return false;
                ConceptAutocompleteDTO cur = a.getValue();
                if (cur == null && expected == null) return true;
                if (cur == null) return false;
                // Compare vocabulary external id + concept external id
                String curVoc = cur.getConceptLabelToDisplay().getVocabulary() != null ? cur.getConceptLabelToDisplay().getVocabulary().getExternalVocabularyId() : null;
                String expVoc = expected.getVocabulary() != null ? expected.getVocabulary().getExternalVocabularyId() : null;
                return Objects.equals(curVoc, expVoc) && Objects.equals(cur.getConceptLabelToDisplay().getConcept().getExternalId(), expected.getExternalId());
            }

            @Override
            public Set<CustomField> dependsOn() {
                return Set.of(observedField);
            }
        };
    }

    @Test
    void applyAll_enablesOrDisablesBasedOnCurrentValues() {
        // Given current = C_A
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(driverField, answerFor(driverField, conceptAdto));
        ValueProvider vp = new MapValueProvider(answers);

        // Two columns, both enabled when driver == C_A
        List<ColumnRule> rules = List.of(
                new ColumnRule(colA, conceptEquals(driverField, conceptA)),
                new ColumnRule(colB, conceptEquals(driverField, conceptA))
        );

        EnabledRulesEngine engine = new EnabledRulesEngine(rules);
        RecordingApplier applier = new RecordingApplier();

        // When
        engine.applyAll(vp, applier);

        // Then both enabled
        assertTrue(applier.enabled(colA));
        assertTrue(applier.enabled(colB));

        // Now change the provider to current = C_B (without calling engine onAnswerChange)
        answers.put(driverField, answerFor(driverField, conceptBdto));
        engine.applyAll(vp, applier);

        // Then both disabled (condition false)
        assertFalse(applier.enabled(colA));
        assertFalse(applier.enabled(colB));
    }

    @Test
    void onAnswerChange_usesOverriddenConcept_notOldValue() {
        // Given current = C_B (old value), but event proposes C_A (new value)
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(driverField, answerFor(driverField, conceptBdto));
        ValueProvider baseVp = new MapValueProvider(answers);

        // colA enabled iff driver == C_A
        ColumnRule rule = new ColumnRule(colA, conceptEquals(driverField, conceptA));
        EnabledRulesEngine engine = new EnabledRulesEngine(List.of(rule));
        RecordingApplier applier = new RecordingApplier();

        // When evaluating with override newConcept = C_A:
        engine.onAnswerChange(driverField, conceptAdto, baseVp, applier);

        // Then: even though base answer was C_B, we should get enabled=true thanks to the override
        assertTrue(applier.enabled(colA));

        // Sanity: if we override with C_B, it should disable
        engine.onAnswerChange(driverField, conceptBdto, baseVp, applier);
        assertFalse(applier.enabled(colA));
    }

    @Test
    void onAnswerChange_updatesOnlyDependentsOfChangedField() {
        // Another independent driver (not changed)
        Concept otherConcept = concept("other", "other");
        CustomField otherDriver = conceptField(20L, "Other Driver", otherConcept);

        // Rules:
        //  - colA depends on driverField == C_A
        //  - colB depends on otherDriver == C_A
        ColumnRule rA = new ColumnRule(colA, conceptEquals(driverField, conceptA));
        ColumnRule rB = new ColumnRule(colB, conceptEquals(otherDriver, conceptA));

        EnabledRulesEngine engine = new EnabledRulesEngine(List.of(rA, rB));

        // Provider has some initial values
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(driverField, answerFor(driverField, conceptBdto));    // currently B
        answers.put(otherDriver, answerFor(otherDriver, conceptBdto));    // currently B
        ValueProvider vp = new MapValueProvider(answers);

        RecordingApplier applier = new RecordingApplier();

        // Change driverField only (override = C_A). Should update colA but not touch colB.
        engine.onAnswerChange(driverField, conceptAdto, vp, applier);

        assertTrue(applier.states.containsKey(colA.getId())); // got recomputed
        assertFalse(applier.states.containsKey(colB.getId())); // untouched
    }

    @Test
    void applyAll_disablesColumn_whenConditionThrows() {
        // Condition that throws to exercise safeTest()
        Condition exploding = new Condition() {
            @Override public boolean test(ValueProvider vp) { throw new RuntimeException("boom"); }
            @Override public Set<CustomField> dependsOn() { return Set.of(driverField); }
        };

        EnabledRulesEngine engine = new EnabledRulesEngine(List.of(new ColumnRule(colA, exploding)));

        // Provider with some value (won't be used)
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(driverField, answerFor(driverField, conceptAdto));
        RecordingApplier applier = new RecordingApplier();

        engine.applyAll(new MapValueProvider(answers), applier);

        // safeTest -> false => column disabled
        assertFalse(applier.enabled(colA));
    }

    @Test
    void instantiateAnswerForField_usedByOverride_canHandleConceptFields() {
        // This test ensures the override path can actually build the temporary answer
        // using AbstractSingleEntity.instantiateAnswerForField and set a Concept.
        CustomFieldAnswerViewModel tmp = CustomFieldAnswerFactory.instantiateAnswerForField(driverField);
        assertTrue(tmp instanceof CustomFieldAnswerSelectOneFromFieldCodeViewModel);

        // emulate the same steps as engine.buildConceptOverride(...)
        CustomFieldAnswerId id = new CustomFieldAnswerId();
        id.setField(driverField);
        tmp.setPk(id);
        ((CustomFieldAnswerSelectOneFromFieldCodeViewModel) tmp).setValue(conceptAdto);

        assertEquals(conceptA, ((CustomFieldAnswerSelectOneFromFieldCodeViewModel) tmp).getValue());
    }
}
