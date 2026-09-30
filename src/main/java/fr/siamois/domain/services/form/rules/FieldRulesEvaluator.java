package fr.siamois.domain.services.form.rules;

import fr.siamois.domain.models.form.rules.ConceptIdLookup;
import fr.siamois.domain.models.form.rules.Condition;
import fr.siamois.domain.models.form.rules.FieldConstraint;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Evaluates the conditional rules of a form's columns against an entity's values — the Java twin
 * of {@code frontend/src/rules/evaluate.ts}, with the same semantics (checked by the shared
 * conformance cases, {@code src/test/resources/form-rules/cases.json}):
 * <ul>
 *   <li>enabledWhen absent → enabled; a condition that can't be evaluated → disabled</li>
 *   <li>EQ / IN: one of the field's values equals one expected value; NEQ / NOT_IN: the negation
 *       (true on an empty field); GT…LTE: the field's single number/date against values[0], false
 *       when empty</li>
 *   <li>required = column required or requiredWhen — never while disabled</li>
 *   <li>rules read values, not other fields' states</li>
 *   <li>a constraint {@code A op B}, declared on A, bounds and flags both sides</li>
 * </ul>
 * A concept expected by a rule compares by its internal id, resolved through {@link ConceptIdLookup}
 * like the layout the front receives.
 */
public final class FieldRulesEvaluator {

    /** A layout column as the evaluator needs it. */
    public record RuledColumn(long fieldId, boolean required, FieldRules rules) {
    }

    public enum IncoherenceKind {DISABLED_WITH_VALUE, OUT_OF_OPTIONS, CONSTRAINT}

    public record Incoherence(IncoherenceKind kind, @Nullable FieldConstraint.Op op, @Nullable Long otherFieldId) {
        static Incoherence of(IncoherenceKind kind) {
            return new Incoherence(kind, null, null);
        }
    }

    public record OptionsContext(String kind, long parentFieldId, @Nullable Long candidateFieldId, @Nullable String value) {
    }

    public record Bound(double value, boolean exclusive, long fieldId) {
    }

    public static final class FieldState {
        private final boolean enabled;
        private final boolean required;
        private final List<Incoherence> incoherent = new ArrayList<>();
        @Nullable
        private OptionsContext optionsContext;
        @Nullable
        private Bound min;
        @Nullable
        private Bound max;

        FieldState(boolean enabled, boolean required) {
            this.enabled = enabled;
            this.required = required;
        }

        public boolean enabled() {
            return enabled;
        }

        public boolean required() {
            return required;
        }

        public List<Incoherence> incoherent() {
            return incoherent;
        }

        @Nullable
        public OptionsContext optionsContext() {
            return optionsContext;
        }

        @Nullable
        public Bound min() {
            return min;
        }

        @Nullable
        public Bound max() {
            return max;
        }

        /** Whether a constraint with {@code otherFieldId} is violated on this field. */
        public boolean violates(long otherFieldId) {
            return incoherent.stream().anyMatch(i -> i.kind() == IncoherenceKind.CONSTRAINT
                    && i.otherFieldId() != null && i.otherFieldId() == otherFieldId);
        }
    }

    private final ConceptIdLookup conceptIds;

    public FieldRulesEvaluator(ConceptIdLookup conceptIds) {
        this.conceptIds = conceptIds;
    }

    public boolean test(Condition condition, Function<Long, Object> valueOf) {
        if (condition instanceof Condition.All all) {
            return all.conditions().stream().allMatch(c -> test(c, valueOf));
        }
        if (condition instanceof Condition.Any any) {
            return any.conditions().stream().anyMatch(c -> test(c, valueOf));
        }
        if (condition instanceof Condition.Not not) {
            return !test(not.condition(), valueOf);
        }
        return testLeaf((Condition.Leaf) condition, valueOf);
    }

    private boolean testLeaf(Condition.Leaf leaf, Function<Long, Object> valueOf) {
        Object value = valueOf.apply(leaf.fieldId());
        List<Object> actual = RuleValues.scalarsOf(value);
        List<Object> expected = leaf.values().stream().map(this::expectedScalar).toList();
        return switch (leaf.op()) {
            case EMPTY -> actual.isEmpty();
            case NOT_EMPTY -> !actual.isEmpty();
            case EQ, IN -> matchesAny(actual, expected);
            case NEQ, NOT_IN -> !matchesAny(actual, expected);
            case GT, GTE, LT, LTE -> {
                Double a = RuleValues.numberOf(value);
                Object e = expected.isEmpty() ? null : expected.get(0);
                yield a != null && e instanceof Double b && compare(a, FieldConstraint.Op.valueOf(leaf.op().name()), b);
            }
        };
    }

    private static boolean matchesAny(List<Object> actual, List<Object> expected) {
        for (Object a : actual) {
            for (Object e : expected) {
                if (e != null && RuleValues.same(a, e)) return true;
            }
        }
        return false;
    }

    @Nullable
    private Object expectedScalar(FieldValueSpec spec) {
        if (spec instanceof FieldValueSpec.ConceptValue c) {
            return conceptIds.conceptId(c.vocabularyExtId(), c.conceptExtId()).map(String::valueOf).orElse(null);
        }
        if (spec instanceof FieldValueSpec.RefValue r) {
            return r.id();
        }
        return RuleValues.scalarOf(((FieldValueSpec.LiteralValue) spec).value());
    }

    private static boolean compare(double a, FieldConstraint.Op op, double b) {
        return switch (op) {
            case GT -> a > b;
            case GTE -> a >= b;
            case LT -> a < b;
            case LTE -> a <= b;
        };
    }

    private static FieldConstraint.Op inverse(FieldConstraint.Op op) {
        return switch (op) {
            case GT -> FieldConstraint.Op.LT;
            case GTE -> FieldConstraint.Op.LTE;
            case LT -> FieldConstraint.Op.GT;
            case LTE -> FieldConstraint.Op.GTE;
        };
    }

    private boolean safe(@Nullable Condition condition, Function<Long, Object> valueOf, boolean whenAbsent) {
        if (condition == null) return whenAbsent;
        try {
            return test(condition, valueOf);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** One state per column, keyed by field id. */
    public Map<Long, FieldState> evaluate(List<RuledColumn> columns, Function<Long, Object> valueOf) {
        Map<Long, FieldState> states = new LinkedHashMap<>();
        for (RuledColumn col : columns) {
            FieldRules rules = col.rules() == null ? FieldRules.NONE : col.rules();
            boolean enabled = safe(rules.enabledWhen(), valueOf, true);
            boolean required = enabled && (col.required() || safe(rules.requiredWhen(), valueOf, false));
            FieldState state = new FieldState(enabled, required);
            if (!enabled && !RuleValues.isEmpty(valueOf.apply(col.fieldId()))) {
                state.incoherent.add(Incoherence.of(IncoherenceKind.DISABLED_WITH_VALUE));
            }
            if (rules.options() instanceof OptionsFilter.RelatedConcepts rc) {
                state.optionsContext = new OptionsContext("RELATED_CONCEPTS", rc.fieldId(), null,
                        RuleValues.idOf(valueOf.apply(rc.fieldId())));
            } else if (rules.options() instanceof OptionsFilter.RefMatch rm) {
                state.optionsContext = new OptionsContext("REF_MATCH", rm.fieldId(), rm.candidateFieldId(),
                        RuleValues.idOf(valueOf.apply(rm.fieldId())));
            }
            states.put(col.fieldId(), state);
        }
        for (RuledColumn col : columns) {
            if (col.rules() == null) continue;
            for (FieldConstraint constraint : col.rules().constraints()) {
                applyConstraint(states.get(col.fieldId()), col.fieldId(), constraint.op(), constraint.fieldId(), valueOf);
                applyConstraint(states.get(constraint.fieldId()), constraint.fieldId(), inverse(constraint.op()), col.fieldId(), valueOf);
            }
        }
        return states;
    }

    private static void applyConstraint(@Nullable FieldState state, long selfId, FieldConstraint.Op op, long otherId,
                                        Function<Long, Object> valueOf) {
        if (state == null) return;
        Double other = RuleValues.numberOf(valueOf.apply(otherId));
        if (other == null) return;
        Bound bound = new Bound(other, op == FieldConstraint.Op.GT || op == FieldConstraint.Op.LT, otherId);
        if (op == FieldConstraint.Op.GT || op == FieldConstraint.Op.GTE) {
            state.min = tighter(state.min, bound, true);
        } else {
            state.max = tighter(state.max, bound, false);
        }
        Double self = RuleValues.numberOf(valueOf.apply(selfId));
        if (self != null && !compare(self, op, other)) {
            state.incoherent.add(new Incoherence(IncoherenceKind.CONSTRAINT, op, otherId));
        }
    }

    private static Bound tighter(@Nullable Bound current, Bound next, boolean isMin) {
        if (current == null) return next;
        if (next.value() == current.value()) return next.exclusive() ? next : current;
        if (isMin) return next.value() > current.value() ? next : current;
        return next.value() < current.value() ? next : current;
    }
}
