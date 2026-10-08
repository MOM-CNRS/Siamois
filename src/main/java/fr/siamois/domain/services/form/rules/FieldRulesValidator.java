package fr.siamois.domain.services.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.rules.Condition;
import fr.siamois.domain.models.form.rules.ConditionOp;
import fr.siamois.domain.models.form.rules.FieldConstraint;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnit;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import fr.siamois.domain.models.form.rules.PlaceSourceSpec;
import fr.siamois.domain.models.form.rules.RuleFieldFamily;
import fr.siamois.domain.services.placesource.PlaceSourceProvider;
import fr.siamois.domain.services.placesource.PlaceSourceRegistry;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class FieldRulesValidator {

    private final PlaceSourceRegistry placeSources;

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
        checkPlaceSources(rules.placeSources(), own, ownFieldId, fields, issues);
        for (FieldConstraint constraint : rules.constraints()) {
            CustomField other = reference(constraint.fieldId(), ownFieldId, fields, issues);
            if (other == null) continue;
            if (own == null || !RuleFieldFamily.of(own).isOrdered() || RuleFieldFamily.of(own) != RuleFieldFamily.of(other)) {
                issues.add(new Issue("rules.error.constraintType", label(other)));
            }
        }
        return issues;
    }

    /**
     * Place sources belong to a place field, name a registered source, and only bind parameters that
     * source declares to another place field of the form.
     */
    private void checkPlaceSources(List<PlaceSourceSpec> specs, CustomField own, long ownFieldId,
                                   Map<Long, CustomField> fields, List<Issue> issues) {
        if (specs.isEmpty()) return;
        if (!isPlaceField(own)) {
            issues.add(new Issue("rules.error.placeSourcesOwnNotPlace"));
        }
        for (PlaceSourceSpec spec : specs) {
            PlaceSourceProvider provider = placeSources.find(spec.source()).orElse(null);
            if (provider == null) {
                issues.add(new Issue("rules.error.placeSourceUnknown", spec.source()));
                continue;
            }
            spec.params().forEach((name, binding) -> {
                if (!provider.declaredParams().contains(name)) {
                    issues.add(new Issue("rules.error.placeSourceParam", name, spec.source()));
                }
                CustomField from = reference(binding.fromField(), ownFieldId, fields, issues);
                if (from != null && !isPlaceField(from)) {
                    issues.add(new Issue("rules.error.placeSourceFromNotPlace", label(from)));
                }
            });
        }
    }

    private static boolean isPlaceField(CustomField field) {
        return field instanceof CustomFieldSelectOneSpatialUnit
                || field instanceof CustomFieldSelectMultipleSpatialUnitTree
                || field instanceof CustomFieldSelectMultipleSpatialUnit;
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
        boolean wrongCount;
        if (none) {
            wrongCount = count != 0;
        } else if (many) {
            wrongCount = count == 0;
        } else {
            wrongCount = count != 1;
        }
        if (wrongCount) {
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
