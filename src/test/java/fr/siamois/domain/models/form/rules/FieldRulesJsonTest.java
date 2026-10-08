package fr.siamois.domain.models.form.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldRulesJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode wire(FieldRules rules) {
        return objectMapper.valueToTree(FieldRulesJson.toWire(rules));
    }

    @Test
    void toWire_writesConceptIdsAsStrings_andOmitsAbsentRules() {
        FieldRules rules = FieldRules.NONE.withEnabledWhen(Condition.eq(-310L, FieldValueSpec.concept(77L)));

        JsonNode json = wire(rules);

        JsonNode leaf = json.get("enabledWhen");
        assertThat(leaf.get("fieldId").asLong()).isEqualTo(-310L);
        assertThat(leaf.get("op").asText()).isEqualTo("EQ");
        JsonNode value = leaf.get("values").get(0);
        assertThat(value.get("conceptId").isTextual()).isTrue();
        assertThat(value.get("conceptId").asText()).isEqualTo("77");
        assertThat(value.has("conceptExtId")).isFalse();
        assertThat(json.has("requiredWhen")).isFalse();
        assertThat(json.has("options")).isFalse();
        assertThat(json.has("constraints")).isFalse();
    }

    @Test
    void roundTrip_preservesEveryRuleKind() {
        FieldRules rules = new FieldRules(
                Condition.all(
                        Condition.in(1L, FieldValueSpec.concept(1L), FieldValueSpec.concept(2L)),
                        Condition.not(Condition.notEmpty(2L)),
                        Condition.any(
                                new Condition.Leaf(3L, ConditionOp.GTE, List.of(FieldValueSpec.literal(10))),
                                new Condition.Leaf(4L, ConditionOp.EQ, List.of(FieldValueSpec.literal("2024-01-01"))),
                                new Condition.Leaf(5L, ConditionOp.NEQ, List.of(FieldValueSpec.ref("42"))))),
                Condition.eq(6L, FieldValueSpec.literal(true)),
                new OptionsFilter.RefMatch(7L, 8L),
                List.of(FieldConstraint.gte(9L), new FieldConstraint(FieldConstraint.Op.LT, 10L)));

        FieldRules back = FieldRulesJson.fromJson(wire(rules));

        assertThat(back.enabledWhen()).isEqualTo(new Condition.All(List.of(
                Condition.in(1L, FieldValueSpec.concept(1L), FieldValueSpec.concept(2L)),
                Condition.not(Condition.notEmpty(2L)),
                Condition.any(
                        new Condition.Leaf(3L, ConditionOp.GTE, List.of(FieldValueSpec.literal(10))),
                        new Condition.Leaf(4L, ConditionOp.EQ, List.of(FieldValueSpec.literal("2024-01-01"))),
                        new Condition.Leaf(5L, ConditionOp.NEQ, List.of(FieldValueSpec.ref("42")))))));
        assertThat(back.requiredWhen()).isEqualTo(rules.requiredWhen());
        assertThat(back.options()).isEqualTo(rules.options());
        assertThat(back.constraints()).isEqualTo(rules.constraints());
    }

    @Test
    void relatedConcepts_roundTrips() {
        FieldRules rules = FieldRules.NONE.withOptions(new OptionsFilter.RelatedConcepts(-310L));

        JsonNode json = wire(rules);

        assertThat(json.get("options").get("kind").asText()).isEqualTo("RELATED_CONCEPTS");
        assertThat(FieldRulesJson.fromJson(json)).isEqualTo(rules);
    }

    @Test
    void dependencies_listsEveryFieldReadByTheRules() {
        FieldRules rules = new FieldRules(
                Condition.eq(1L, FieldValueSpec.literal("x")),
                Condition.notEmpty(2L),
                new OptionsFilter.RelatedConcepts(3L),
                List.of(FieldConstraint.lte(4L)));

        assertThat(rules.dependencies()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void fromJson_rejectsAMalformedCondition() {
        JsonNode bad = objectMapper.valueToTree(Map.of("enabledWhen", Map.of("op", "EQ")));

        assertThatThrownBy(() -> FieldRulesJson.fromJson(bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromJson_nullIsNone() {
        assertThat(FieldRulesJson.fromJson(null)).isSameAs(FieldRules.NONE);
    }
}
