package fr.siamois.ui.form;


import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerId;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldCode;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.form.rules.ColumnApplier;
import fr.siamois.ui.form.rules.ColumnRule;
import fr.siamois.ui.form.rules.Condition;
import fr.siamois.ui.form.rules.ValueProvider;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Moteur minimal : index des dépendances champ -> colonnes, évaluation initiale et ciblée.
 */
public final class EnabledRulesEngine {

    private final Map<CustomField, ColumnRule> rulesByCol;
    private final Map<CustomField, Set<CustomField>> dependentsByField; // field -> set of column fields

    public EnabledRulesEngine(List<ColumnRule> rules) {
        Objects.requireNonNull(rules, "rules must not be null");
        this.rulesByCol = rules.stream()
                .collect(Collectors.toUnmodifiableMap(ColumnRule::columnField,
                        r -> r
                ));

        // index des dépendances
        Map<CustomField, Set<CustomField>> idx = new HashMap<>();
        for (ColumnRule r : rules) {
            for (CustomField f : r.enabledWhen().dependsOn()) {
                idx.computeIfAbsent(f, customField -> new LinkedHashSet<>()).add(r.columnField());
            }
        }
        this.dependentsByField = idx;
    }

    /** Évalue toutes les colonnes (initialisation). */
    public void applyAll(ValueProvider vp, ColumnApplier applier) {
        Objects.requireNonNull(vp, "vp must not be null");
        Objects.requireNonNull(applier, "applier must not be null");
        for (ColumnRule r : rulesByCol.values()) {
            boolean enabled = safeTest(r.enabledWhen(), vp);
            applier.setColumnEnabled(r.columnField(), enabled);
        }
    }

    private CustomFieldAnswer buildConceptOverride(CustomField field, Concept concept) {
        CustomFieldAnswer answer = CustomFieldAnswerFactory.instantiateAnswerForField(field);
        CustomFieldAnswerId id = new CustomFieldAnswerId();
        id.setField(field);
        answer.setPk(id);

        if (answer instanceof CustomFieldAnswerSelectOneFromFieldCode a) {
            a.setValue(concept);
        } else {
            throw new IllegalArgumentException(
                    "Field " + field.getLabel() + " does not accept a Concept value"
            );
        }

        return answer;
    }

    /** À appeler quand la réponse d'un champ a changé. */
    public void onAnswerChange(
            CustomField changedField,
            Concept newConcept,
            ValueProvider baseVp,
            ColumnApplier applier
    ) {
        Objects.requireNonNull(changedField, "changedField must not be null");
        Objects.requireNonNull(baseVp, "baseVp must not be null");
        Objects.requireNonNull(applier, "applier must not be null");

        // Build a temporary answer holding the proposed concept
        CustomFieldAnswer proposedAnswer = buildConceptOverride(changedField, newConcept);

        // Wrap the base ValueProvider: return our proposedAnswer for this field
        ValueProvider overridingVp = f ->
                f.equals(changedField) ? proposedAnswer : baseVp.getCurrentAnswer(f);

        Set<CustomField> impactedCols =
                dependentsByField.getOrDefault(changedField, java.util.Collections.emptySet());
        if (impactedCols.isEmpty()) return;

        for (CustomField colField : impactedCols) {
            ColumnRule rule = rulesByCol.get(colField);
            if (rule == null) continue;

            boolean enabled = safeTest(rule.enabledWhen(), overridingVp);
            applier.setColumnEnabled(colField, enabled);
        }
    }

    private static boolean safeTest(Condition c, ValueProvider vp) {
        try {
            return c.test(vp);
        } catch (RuntimeException ex) {
            // If we have an error we disable the column
            return false;
        }
    }
}
