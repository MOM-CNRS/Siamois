package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/** Normalise les réponses formulaire (OpenAPI {@link AnswerInput} + payload mobile legacy). */
public final class FieldAnswerMaps {

    private static final String VALUES = "values";

    private FieldAnswerMaps() {
    }

    /**
     * Ids to add to and remove from a multi-valued answer — what an {@link AnswerInput} with
     * {@code add}/{@code remove} asks for, instead of a whole new list.
     */
    public record Delta(List<Object> add, List<Object> remove) {

        /**
         * {@code current} with {@code remove} taken out and {@code added} appended (those not
         * already in it), in the collection {@code empty} provides — a Set or a List, whichever
         * the field's value is held in.
         *
         * @param idOf the id an item of {@code current} or {@code added} is referenced by
         */
        public <T, C extends Collection<T>> C applyTo(@Nullable Collection<? extends T> current, Collection<? extends T> added,
                                                      ToLongFunction<Object> idOf, C empty) {
            Set<Long> removed = remove.stream().map(idOf::applyAsLong).collect(Collectors.toSet());
            Set<Long> kept = new HashSet<>();
            if (current != null) {
                for (T item : current) {
                    long id = idOf.applyAsLong(item);
                    if (!removed.contains(id) && kept.add(id)) empty.add(item);
                }
            }
            for (T item : added) {
                if (kept.add(idOf.applyAsLong(item))) empty.add(item);
            }
            return empty;
        }
    }

    public static Map<String, Object> merge(Map<String, AnswerInput> answers,
                                     Map<String, Object> legacyFieldAnswers) {
        Map<String, Object> out = new HashMap<>();
        if (answers != null) {
            // A delta stays an AnswerInput: unwrapping it would lose what it adds and removes.
            answers.forEach((k, v) -> out.put(k, v != null && v.isDelta() ? v : unwrap(v)));
        }
        if (legacyFieldAnswers != null) {
            legacyFieldAnswers.forEach((k, v) -> out.put(k, unwrap(v)));
        }
        return out;
    }

    /**
     * The ids an answer adds and removes, or null if it sets its value instead. An answer can't do
     * both: {@code values} together with {@code add}/{@code remove} is a 400.
     */
    @Nullable
    public static Delta delta(Object raw) {
        List<?> add = null;
        List<?> remove = null;
        boolean setsValues = false;
        if (raw instanceof AnswerInput ai) {
            add = ai.add();
            remove = ai.remove();
            setsValues = ai.values() != null;
        } else if (raw instanceof Map<?, ?> map) {
            add = listOf(map.get("add"), "add");
            remove = listOf(map.get("remove"), "remove");
            setsValues = map.get(VALUES) != null;
        }
        if (add == null && remove == null) return null;
        if (setsValues) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "values et add/remove ne se combinent pas dans une même réponse");
        }
        return new Delta(add == null ? List.of() : List.copyOf(add.stream().filter(Objects::nonNull).toList()),
                remove == null ? List.of() : List.copyOf(remove.stream().filter(Objects::nonNull).toList()));
    }

    @Nullable
    private static List<?> listOf(@Nullable Object value, String name) {
        if (value == null) return null;
        if (value instanceof List<?> list) return list;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " doit être une liste d'identifiants");
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
            if (map.containsKey(VALUES)) {
                return map.get(VALUES);
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
