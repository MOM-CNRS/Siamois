package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Normalise les réponses formulaire (OpenAPI {@link AnswerInput} + payload mobile legacy). */
public final class FieldAnswerMaps {

    private FieldAnswerMaps() {
    }

    public static Map<String, Object> merge(Map<String, AnswerInput> answers,
                                     Map<String, Object> legacyFieldAnswers) {
        Map<String, Object> out = new HashMap<>();
        if (answers != null) {
            answers.forEach((k, v) -> out.put(k, unwrap(v)));
        }
        if (legacyFieldAnswers != null) {
            legacyFieldAnswers.forEach((k, v) -> out.put(k, unwrap(v)));
        }
        return out;
    }

    public static Object unwrap(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof AnswerInput ai) {
            if (ai.values() != null) {
                return ai.values();
            }
            return ai.value();
        }
        if (raw instanceof Map<?, ?> map) {
            if (map.containsKey("values")) {
                return map.get("values");
            }
            if (map.containsKey("value")) {
                return map.get("value");
            }
        }
        if (raw instanceof List<?> list) {
            return list;
        }
        return raw;
    }
}
