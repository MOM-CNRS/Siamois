package fr.siamois.domain.services.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.rules.Condition;
import fr.siamois.domain.models.form.rules.ConditionOp;
import fr.siamois.domain.models.form.rules.FieldConstraint;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import fr.siamois.domain.models.form.rules.RuleFieldFamily;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the rules a user wrote before they are stored: they may only read other fields of the same
 * form, with operators and values that mean something for the kind of field read. A field that is
 * inactive for the type is fine — a rule reads it as empty.
 */
@Component
public class FieldRulesValidator {

    public static final int MAX_LEAVES = 20;

    /** A problem found in a rule: a message key and its arguments. */
    public record Issue(String key, List<Object> args) {
        public Issue(String key, Object... args) {
            this(key, List.of(args));
        }
    }

    private static final Set<ConditionOp> EMPTINESS = EnumSet.of(ConditionOp.EMPTY, ConditionOp.NOT_EMPTY);
    private static final Set<ConditionOp> EQUALITY = EnumSet.of(ConditionOp.EQ, ConditionOp.NEQ, ConditionOp.IN,
            ConditionOp.NOT_IN, ConditionOp.EMPTY, ConditionOp.NOT_EMPTY);

    /**
     * @param ownFieldId the field the rules belong to
     * @param fields     the fields of the form by id
     */
    public List<Issue> validate(FieldRules rules, long ownFieldId, Map<Long, CustomField> fields) {
        List<Issue> issues = new ArrayList<>();
        CustomField own = fields.get(ownFieldId);
        int[] leaves = {0};
        check(rules.enabledWhen(), ownFieldId, fields, issues, leaves);
        check(rules.requiredWhen(), ownFieldId, fields, issues, leaves);
        if (leaves[0] > MAX_LEAVES) {
            issues.add(new Issue("rules.error.tooBig", MAX_LEAVES));
        }
        OptionsFilter options = rules.options();
        if (options instanceof OptionsFilter.RelatedConcepts related) {
            if (own == null || RuleFieldFamily.of(own) != RuleFieldFamily.CONCEPT) {
                issues.add(new Issue("rules.error.optionsOwnNotConcept"));
            }
            CustomField source = reference(related.fieldId(), ownFieldId, fields, issues);
            if (source != null && RuleFieldFamily.of(source) != RuleFieldFamily.CONCEPT) {
                issues.add(new Issue("rules.error.optionsNotConcept", label(source)));
            }
        } else if (options instanceof OptionsFilter.RefMatch match) {
            reference(match.fieldId(), ownFieldId, fields, issues);
        }
        for (FieldConstraint constraint : rules.constraints()) {
            CustomField other = reference(constraint.fieldId(), ownFieldId, fields, issues);
            if (other == null) continue;
            if (own == null || !RuleFieldFamily.of(own).isOrdered() || RuleFieldFamily.of(own) != RuleFieldFamily.of(other)) {
                issues.add(new Issue("rules.error.constraintType", label(other)));
            }
        }
        return issues;
    }

    private void check(Condition condition, long ownFieldId, Map<Long, CustomField> fields, List<Issue> issues, int[] leaves) {
        if (condition == null) return;
        if (condition instanceof Condition.All all) {
            all.conditions().forEach(c -> check(c, ownFieldId, fields, issues, leaves));
        } else if (condition instanceof Condition.Any any) {
            any.conditions().forEach(c -> check(c, ownFieldId, fields, issues, leaves));
        } else if (condition instanceof Condition.Not not) {
            check(not.condition(), ownFieldId, fields, issues, leaves);
        } else if (condition instanceof Condition.Leaf leaf) {
            leaves[0]++;
            checkLeaf(leaf, ownFieldId, fields, issues);
        }
    }

    private void checkLeaf(Condition.Leaf leaf, long ownFieldId, Map<Long, CustomField> fields, List<Issue> issues) {
        CustomField field = reference(leaf.fieldId(), ownFieldId, fields, issues);
        if (field == null) return;
        RuleFieldFamily family = RuleFieldFamily.of(field);
        Set<ConditionOp> allowed = switch (family) {
            case NUMBER, DATE -> EnumSet.allOf(ConditionOp.class);
            case CONCEPT, TEXT -> EQUALITY;
            case OTHER -> EMPTINESS;
        };
        if (!allowed.contains(leaf.op())) {
            issues.add(new Issue("rules.error.opNotAllowed", leaf.op().name(), label(field)));
            return;
        }
        int count = leaf.values().size();
        boolean many = leaf.op() == ConditionOp.IN || leaf.op() == ConditionOp.NOT_IN;
        boolean none = EMPTINESS.contains(leaf.op());
        if (none ? count != 0 : many ? count == 0 : count != 1) {
            issues.add(new Issue("rules.error.valueCount", label(field)));
            return;
        }
        for (FieldValueSpec value : leaf.values()) {
            if (!matches(family, value)) {
                issues.add(new Issue("rules.error.valueType", label(field)));
                return;
            }
        }
    }

    private boolean matches(RuleFieldFamily family, FieldValueSpec value) {
        return switch (family) {
            case CONCEPT -> value instanceof FieldValueSpec.ConceptValue;
            case NUMBER -> value instanceof FieldValueSpec.LiteralValue literal && literal.value() instanceof Number;
            case TEXT -> value instanceof FieldValueSpec.LiteralValue literal && literal.value() instanceof String;
            case DATE -> value instanceof FieldValueSpec.LiteralValue literal && literal.value() instanceof String s && isDate(s);
            case OTHER -> false;
        };
    }

    private static boolean isDate(String value) {
        try {
            LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private CustomField reference(long fieldId, long ownFieldId, Map<Long, CustomField> fields, List<Issue> issues) {
        if (fieldId == ownFieldId) {
            issues.add(new Issue("rules.error.selfReference"));
            return null;
        }
        CustomField field = fields.get(fieldId);
        if (field == null) {
            issues.add(new Issue("rules.error.unknownField", fieldId));
        }
        return field;
    }

    private static String label(CustomField field) {
        return field.getLabel();
    }
}
