package fr.siamois.domain.models.form.rules;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Condition d'une règle de champ ({@code enabledWhen}, {@code requiredWhen}) : une feuille qui
 * compare la valeur d'un autre champ, ou une composition ET / OU / NON.
 * <p>
 * Forme JSON (voir {@link FieldRulesJson}) : {@code {"all":[...]}}, {@code {"any":[...]}},
 * {@code {"not":c}} ou {@code {"fieldId":12,"op":"EQ","values":[...]}}. Le même langage est évalué
 * côté front ({@code frontend/src/rules/evaluate.ts}) et au PATCH ({@code FieldRulesEvaluator}).
 */
public sealed interface Condition extends Serializable
        permits Condition.All, Condition.Any, Condition.Not, Condition.Leaf {

    /** Les champs lus par cette condition. */
    default Set<Long> fieldIds() {
        Set<Long> out = new LinkedHashSet<>();
        collectFieldIds(out);
        return out;
    }

    void collectFieldIds(Set<Long> out);

    record All(List<Condition> conditions) implements Condition {
        public All {
            conditions = List.copyOf(conditions);
        }

        @Override
        public void collectFieldIds(Set<Long> out) {
            conditions.forEach(c -> c.collectFieldIds(out));
        }
    }

    record Any(List<Condition> conditions) implements Condition {
        public Any {
            conditions = List.copyOf(conditions);
        }

        @Override
        public void collectFieldIds(Set<Long> out) {
            conditions.forEach(c -> c.collectFieldIds(out));
        }
    }

    record Not(Condition condition) implements Condition {
        public Not {
            Objects.requireNonNull(condition, "condition");
        }

        @Override
        public void collectFieldIds(Set<Long> out) {
            condition.collectFieldIds(out);
        }
    }

    /** Compare la valeur du champ {@code fieldId} aux {@code values} (vides pour EMPTY/NOT_EMPTY). */
    record Leaf(long fieldId, ConditionOp op, List<FieldValueSpec> values) implements Condition {
        public Leaf {
            Objects.requireNonNull(op, "op");
            values = values == null ? List.of() : List.copyOf(values);
        }

        @Override
        public void collectFieldIds(Set<Long> out) {
            out.add(fieldId);
        }
    }

    static Condition all(Condition... conditions) {
        return new All(List.of(conditions));
    }

    static Condition any(Condition... conditions) {
        return new Any(List.of(conditions));
    }

    static Condition not(Condition condition) {
        return new Not(condition);
    }

    static Condition eq(long fieldId, FieldValueSpec value) {
        return new Leaf(fieldId, ConditionOp.EQ, List.of(value));
    }

    static Condition in(long fieldId, FieldValueSpec... values) {
        return new Leaf(fieldId, ConditionOp.IN, List.of(values));
    }

    static Condition notEmpty(long fieldId) {
        return new Leaf(fieldId, ConditionOp.NOT_EMPTY, List.of());
    }
}
