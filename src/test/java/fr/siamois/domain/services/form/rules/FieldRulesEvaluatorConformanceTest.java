package fr.siamois.domain.services.form.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.form.rules.FieldRulesJson;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the conformance cases shared with the TS evaluator
 * ({@code frontend/src/rules/evaluate.conformance.test.ts}): both sides must produce the same
 * states for the same rules and values.
 */
class FieldRulesEvaluatorConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> sharedCases() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/form-rules/cases.json")) {
            root = MAPPER.readTree(in);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> run(c)));
        }
        return tests.stream();
    }

    private void run(JsonNode c) {
        List<FieldRulesEvaluator.RuledColumn> columns = new ArrayList<>();
        for (JsonNode col : c.get("columns")) {
            columns.add(new FieldRulesEvaluator.RuledColumn(
                    col.get("fieldId").asLong(),
                    col.path("isRequired").asBoolean(false),
                    FieldRulesJson.fromJson(col.get("rules"))));
        }
        Map<Long, Object> values = new HashMap<>();
        c.get("values").fields().forEachRemaining(e -> values.put(Long.parseLong(e.getKey()), MAPPER.convertValue(e.getValue(), Object.class)));

        Map<Long, FieldRulesEvaluator.FieldState> states = new FieldRulesEvaluator().evaluate(columns, values::get);

        c.get("expected").fields().forEachRemaining(e -> {
            JsonNode expected = e.getValue();
            FieldRulesEvaluator.FieldState state = states.get(Long.parseLong(e.getKey()));
            assertThat(state).as("state of field %s", e.getKey()).isNotNull();
            assertThat(reparsed(actualSubset(state, expected))).as("field %s", e.getKey()).isEqualTo(reparsed(expected));
        });
    }

    private static Map<String, Object> actualSubset(FieldRulesEvaluator.FieldState state, JsonNode expected) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (expected.has("enabled")) out.put("enabled", state.enabled());
        if (expected.has("required")) out.put("required", state.required());
        if (expected.has("incoherent")) {
            out.put("incoherent", state.incoherent().stream().map(i -> i.kind().name()).toList());
        }
        if (expected.has("optionsContext")) {
            FieldRulesEvaluator.OptionsContext ctx = state.optionsContext();
            Map<String, Object> m = null;
            if (ctx != null) {
                m = new LinkedHashMap<>();
                m.put("kind", ctx.kind());
                m.put("parentFieldId", String.valueOf(ctx.parentFieldId()));
                if (ctx.candidateFieldId() != null) m.put("candidateFieldId", String.valueOf(ctx.candidateFieldId()));
                m.put(ctx.kind().equals("RELATED_CONCEPTS") ? "relatedTo" : "value", ctx.value());
            }
            out.put("optionsContext", m);
        }
        if (expected.has("bounds")) {
            Map<String, Object> bounds = new LinkedHashMap<>();
            if (expected.get("bounds").has("min")) bounds.put("min", bound(state.min()));
            if (expected.get("bounds").has("max")) bounds.put("max", bound(state.max()));
            out.put("bounds", bounds);
        }
        return out;
    }

    /** Through JSON text and back, so numbers get the same node type on both sides of the comparison. */
    private static JsonNode reparsed(Object value) {
        try {
            return MAPPER.readTree(MAPPER.writeValueAsString(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> bound(FieldRulesEvaluator.Bound b) {
        if (b == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        double v = b.value();
        m.put("value", v == Math.rint(v) ? (Object) (long) v : v);
        m.put("exclusive", b.exclusive());
        return m;
    }

}
