package fr.siamois.domain.models.form.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Codec JSON du langage de règles — la seule définition du format, partagée par la sérialisation
 * des layouts vers le front, la lecture des cas de conformité et (phase 2) la persistance.
 * <pre>
 * "rules": {
 *   "enabledWhen":  Condition,
 *   "requiredWhen": Condition,
 *   "options":      {"kind":"RELATED_CONCEPTS","fieldId":1} | {"kind":"REF_MATCH","fieldId":1,"candidateFieldId":2},
 *   "constraints":  [{"op":"GTE","fieldId":3}]
 * }
 * Condition  = {"all":[..]} | {"any":[..]} | {"not":c} | {"fieldId":1,"op":"EQ","values":[FieldValue]}
 * FieldValue = {CONCEPT_ID} | {"id"} | littéral
 * </pre>
 */
public final class FieldRulesJson {

    private static final String CONCEPT_ID = "conceptId";

    private static final String FIELD_ID = "fieldId";
    private static final String KIND_RELATED_CONCEPTS = "RELATED_CONCEPTS";
    private static final String KIND_REF_MATCH = "REF_MATCH";

    private FieldRulesJson() {
        throw new UnsupportedOperationException();
    }

    // ------------------------------------------------------------------ écriture

    /**
     * Forme « fil » des règles, en maps/lists sérialisables par Jackson — aussi la forme stockée. Un
     * concept est son {@code conceptId} en chaîne, comme {@code ResourceRef.resourceId}.
     */
    public static Map<String, Object> toWire(FieldRules rules) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (rules.enabledWhen() != null) out.put("enabledWhen", conditionToWire(rules.enabledWhen()));
        if (rules.requiredWhen() != null) out.put("requiredWhen", conditionToWire(rules.requiredWhen()));
        if (rules.options() != null) out.put("options", optionsToWire(rules.options()));
        if (!rules.constraints().isEmpty()) {
            out.put("constraints", rules.constraints().stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("op", c.op().name());
                m.put(FIELD_ID, c.fieldId());
                return m;
            }).toList());
        }
        return out;
    }

    private static Map<String, Object> conditionToWire(Condition condition) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (condition instanceof Condition.All all) {
            m.put("all", all.conditions().stream().map(c -> conditionToWire(c)).toList());
        } else if (condition instanceof Condition.Any any) {
            m.put("any", any.conditions().stream().map(c -> conditionToWire(c)).toList());
        } else if (condition instanceof Condition.Not not) {
            m.put("not", conditionToWire(not.condition()));
        } else if (condition instanceof Condition.Leaf leaf) {
            m.put(FIELD_ID, leaf.fieldId());
            m.put("op", leaf.op().name());
            m.put("values", leaf.values().stream().map(v -> valueToWire(v)).toList());
        }
        return m;
    }

    private static Object valueToWire(FieldValueSpec value) {
        if (value instanceof FieldValueSpec.ConceptValue c) {
            return Map.of(CONCEPT_ID, String.valueOf(c.conceptId()));
        }
        if (value instanceof FieldValueSpec.RefValue r) {
            return Map.of("id", r.id());
        }
        return ((FieldValueSpec.LiteralValue) value).value();
    }

    private static Map<String, Object> optionsToWire(OptionsFilter filter) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (filter instanceof OptionsFilter.RelatedConcepts rc) {
            m.put("kind", KIND_RELATED_CONCEPTS);
            m.put(FIELD_ID, rc.fieldId());
        } else if (filter instanceof OptionsFilter.RefMatch rm) {
            m.put("kind", KIND_REF_MATCH);
            m.put(FIELD_ID, rm.fieldId());
            m.put("candidateFieldId", rm.candidateFieldId());
        }
        return m;
    }

    // ------------------------------------------------------------------ lecture

    /** Relit la forme fil. */
    public static FieldRules fromJson(@Nullable JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return FieldRules.NONE;
        }
        List<FieldConstraint> constraints = elements(node.get("constraints"))
                .map(c -> new FieldConstraint(FieldConstraint.Op.valueOf(c.get("op").asText()), c.get(FIELD_ID).asLong()))
                .toList();
        return new FieldRules(
                conditionOrNull(node.get("enabledWhen")),
                conditionOrNull(node.get("requiredWhen")),
                optionsOrNull(node.get("options")),
                constraints);
    }

    @Nullable
    private static Condition conditionOrNull(@Nullable JsonNode node) {
        return node == null || node.isNull() ? null : conditionFromJson(node);
    }

    public static Condition conditionFromJson(JsonNode node) {
        if (node.has("all")) return new Condition.All(conditionList(node.get("all")));
        if (node.has("any")) return new Condition.Any(conditionList(node.get("any")));
        if (node.has("not")) return new Condition.Not(conditionFromJson(node.get("not")));
        if (!node.has(FIELD_ID) || !node.has("op")) {
            throw new IllegalArgumentException("Invalid condition: " + node);
        }
        List<FieldValueSpec> values = elements(node.get("values")).map(FieldRulesJson::valueFromJson).toList();
        return new Condition.Leaf(node.get(FIELD_ID).asLong(), ConditionOp.valueOf(node.get("op").asText()), values);
    }

    private static List<Condition> conditionList(JsonNode array) {
        return elements(array).map(FieldRulesJson::conditionFromJson).toList();
    }

    private static FieldValueSpec valueFromJson(JsonNode v) {
        if (v.isObject()) {
            if (v.has(CONCEPT_ID)) {
                return new FieldValueSpec.ConceptValue(v.get(CONCEPT_ID).asLong());
            }
            if (v.has("id")) {
                return new FieldValueSpec.RefValue(v.get("id").asText());
            }
            throw new IllegalArgumentException("Invalid value: " + v);
        }
        if (v.isNumber()) return new FieldValueSpec.LiteralValue(v.numberValue());
        if (v.isBoolean()) return new FieldValueSpec.LiteralValue(v.booleanValue());
        return new FieldValueSpec.LiteralValue(v.asText());
    }

    @Nullable
    private static OptionsFilter optionsOrNull(@Nullable JsonNode node) {
        if (node == null || node.isNull()) return null;
        String kind = node.path("kind").asText();
        return switch (kind) {
            case KIND_RELATED_CONCEPTS -> new OptionsFilter.RelatedConcepts(node.get(FIELD_ID).asLong());
            case KIND_REF_MATCH -> new OptionsFilter.RefMatch(node.get(FIELD_ID).asLong(), node.get("candidateFieldId").asLong());
            default -> throw new IllegalArgumentException("Unknown options kind: " + kind);
        };
    }

    private static java.util.stream.Stream<JsonNode> elements(@Nullable JsonNode array) {
        return java.util.stream.StreamSupport.stream(iterable(array).spliterator(), false);
    }

    private static Iterable<JsonNode> iterable(@Nullable JsonNode array) {
        return array == null || !array.isArray() ? List.of() : array;
    }
}
