package fr.siamois.domain.services.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.form.rules.Condition;
import fr.siamois.domain.models.form.rules.ConditionOp;
import fr.siamois.domain.models.form.rules.FieldConstraint;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FieldRulesValidatorTest {

    private final FieldRulesValidator validator = new FieldRulesValidator(new fr.siamois.domain.services.placesource.PlaceSourceRegistry(List.of(geoplatSource())));

    private final CustomField concept = conceptField(1L, "nature");
    private final CustomField otherConcept = conceptField(2L, "interpretation");
    private final CustomField number = CustomFieldInteger.builder().id(3L).label("zsup").build();
    private final CustomField otherNumber = CustomFieldInteger.builder().id(4L).label("zinf").build();
    private final CustomField text = CustomFieldText.builder().id(5L).label("description").build();
    private final CustomField date = CustomFieldDateTime.builder().id(6L).label("opening").build();
    private final Map<Long, CustomField> fields = List.of(concept, otherConcept, number, otherNumber, text, date).stream()
            .collect(Collectors.toMap(CustomField::getId, f -> f));

    private static CustomField conceptField(long id, String label) {
        CustomFieldSelectOne field = new CustomFieldSelectOne();
        field.setId(id);
        field.setLabel(label);
        return field;
    }

    private List<String> keys(FieldRules rules, long own) {
        return validator.validate(rules, own, fields).stream().map(FieldRulesValidator.Issue::key).toList();
    }

    @Test
    void aSoundRuleHasNoIssue() {
        FieldRules rules = new FieldRules(Condition.in(1L, FieldValueSpec.concept(77)), Condition.notEmpty(5L),
                new OptionsFilter.RelatedConcepts(1L), List.of());
        assertThat(keys(rules, 2L)).isEmpty();
    }

    @Test
    void aRuleCannotReadItsOwnFieldOrAFieldOutsideTheForm() {
        assertThat(keys(FieldRules.NONE.withEnabledWhen(Condition.notEmpty(2L)), 2L)).containsExactly("rules.error.selfReference");
        assertThat(keys(FieldRules.NONE.withEnabledWhen(Condition.notEmpty(99L)), 2L)).containsExactly("rules.error.unknownField");
    }

    @Test
    void anOperatorMustSuitTheKindOfFieldRead() {
        Condition gtOnText = new Condition.Leaf(5L, ConditionOp.GT, List.of(FieldValueSpec.literal("a")));
        Condition gtOnNumber = new Condition.Leaf(3L, ConditionOp.GT, List.of(FieldValueSpec.literal(3)));
        assertThat(keys(FieldRules.NONE.withEnabledWhen(gtOnText), 1L)).containsExactly("rules.error.opNotAllowed");
        assertThat(keys(FieldRules.NONE.withEnabledWhen(gtOnNumber), 1L)).isEmpty();
    }

    @Test
    void valuesMustMatchTheFieldAndTheOperator() {
        Condition wrongType = new Condition.Leaf(3L, ConditionOp.EQ, List.of(FieldValueSpec.literal("x")));
        Condition noValue = new Condition.Leaf(3L, ConditionOp.EQ, List.of());
        Condition valueOnEmpty = new Condition.Leaf(3L, ConditionOp.EMPTY, List.of(FieldValueSpec.literal(1)));
        Condition badDate = new Condition.Leaf(6L, ConditionOp.GTE, List.of(FieldValueSpec.literal("yesterday")));
        Condition goodDate = new Condition.Leaf(6L, ConditionOp.GTE, List.of(FieldValueSpec.literal("2026-01-31")));
        assertThat(keys(FieldRules.NONE.withEnabledWhen(wrongType), 1L)).containsExactly("rules.error.valueType");
        assertThat(keys(FieldRules.NONE.withEnabledWhen(noValue), 1L)).containsExactly("rules.error.valueCount");
        assertThat(keys(FieldRules.NONE.withEnabledWhen(valueOnEmpty), 1L)).containsExactly("rules.error.valueCount");
        assertThat(keys(FieldRules.NONE.withEnabledWhen(badDate), 1L)).containsExactly("rules.error.valueType");
        assertThat(keys(FieldRules.NONE.withEnabledWhen(goodDate), 1L)).isEmpty();
    }

    @Test
    void optionsNeedConceptFieldsOnBothSides() {
        assertThat(keys(FieldRules.NONE.withOptions(new OptionsFilter.RelatedConcepts(3L)), 2L))
                .containsExactly("rules.error.optionsNotConcept");
        assertThat(keys(FieldRules.NONE.withOptions(new OptionsFilter.RelatedConcepts(1L)), 3L))
                .containsExactly("rules.error.optionsOwnNotConcept");
    }

    @Test
    void constraintsNeedTwoFieldsOfTheSameOrderedKind() {
        assertThat(keys(FieldRules.NONE.withConstraints(FieldConstraint.gte(4L)), 3L)).isEmpty();
        assertThat(keys(FieldRules.NONE.withConstraints(FieldConstraint.gte(6L)), 3L)).containsExactly("rules.error.constraintType");
        assertThat(keys(FieldRules.NONE.withConstraints(FieldConstraint.gte(5L)), 5L)).containsExactly("rules.error.selfReference");
    }

    @Test
    void aRuleIsBoundedInSize() {
        Condition[] many = new Condition[FieldRulesValidator.MAX_LEAVES + 1];
        java.util.Arrays.fill(many, Condition.notEmpty(5L));
        assertThat(keys(FieldRules.NONE.withEnabledWhen(Condition.all(many)), 1L)).containsExactly("rules.error.tooBig");
    }

    private static fr.siamois.domain.services.placesource.PlaceSourceProvider geoplatSource() {
        fr.siamois.domain.services.placesource.PlaceSourceProvider provider = org.mockito.Mockito.mock(fr.siamois.domain.services.placesource.PlaceSourceProvider.class);
        org.mockito.Mockito.when(provider.id()).thenReturn("GEOPLAT");
        org.mockito.Mockito.when(provider.declaredParams()).thenReturn(java.util.Set.of("citycode"));
        return provider;
    }

    private static CustomField placeField(long id, String label) {
        return fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit.builder().id(id).label(label).build();
    }

    private Map<Long, CustomField> withPlaces() {
        Map<Long, CustomField> all = new java.util.HashMap<>(fields);
        all.put(20L, placeField(20L, "commune"));
        all.put(21L, placeField(21L, "précis"));
        return all;
    }

    private List<String> placeKeys(fr.siamois.domain.models.form.rules.PlaceSourceSpec spec, long own) {
        return validator.validate(FieldRules.NONE.withPlaceSources(spec), own, withPlaces()).stream()
                .map(FieldRulesValidator.Issue::key).toList();
    }

    private static fr.siamois.domain.models.form.rules.PlaceSourceSpec bound(String source, String param, long from) {
        return new fr.siamois.domain.models.form.rules.PlaceSourceSpec(source, Map.of(param,
                new fr.siamois.domain.models.form.rules.PlaceSourceSpec.ParamBinding(from,
                        fr.siamois.domain.models.form.rules.PlaceSourceSpec.PlaceAttribute.CODE)),
                fr.siamois.domain.models.form.rules.PlaceSourceSpec.OnMissing.SKIP);
    }

    @Test
    void aPlaceSourceBoundToAnotherPlaceFieldIsSound() {
        assertThat(placeKeys(bound("GEOPLAT", "citycode", 20L), 21L)).isEmpty();
    }

    @Test
    void placeSourcesNeedAPlaceFieldARegisteredSourceADeclaredParameterAndAPlaceToReadFrom() {
        assertThat(placeKeys(bound("GEOPLAT", "citycode", 20L), 5L)).contains("rules.error.placeSourcesOwnNotPlace");
        assertThat(placeKeys(bound("NOPE", "citycode", 20L), 21L)).containsExactly("rules.error.placeSourceUnknown");
        assertThat(placeKeys(bound("GEOPLAT", "zip", 20L), 21L)).containsExactly("rules.error.placeSourceParam");
        assertThat(placeKeys(bound("GEOPLAT", "citycode", 5L), 21L)).containsExactly("rules.error.placeSourceFromNotPlace");
        assertThat(placeKeys(bound("GEOPLAT", "citycode", 21L), 21L)).containsExactly("rules.error.selfReference");
        assertThat(placeKeys(bound("GEOPLAT", "citycode", 99L), 21L)).containsExactly("rules.error.unknownField");
    }
}
