package fr.siamois.ui.form.rules;


import fr.siamois.domain.models.form.customfield.CustomField;

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
                idx.computeIfAbsent(f, __ -> new LinkedHashSet<>()).add(r.columnField());
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

    /** À appeler quand la réponse d'un champ a changé. */
    public void onAnswerChange(CustomField changedField, ValueProvider vp, ColumnApplier applier) {
        Objects.requireNonNull(changedField, "changedField must not be null");
        Objects.requireNonNull(vp, "vp must not be null");
        Objects.requireNonNull(applier, "applier must not be null");

        Set<CustomField> impactedCols = dependentsByField.getOrDefault(changedField, Collections.emptySet());
        if (impactedCols.isEmpty()) return;

        for (CustomField colField : impactedCols) {
            ColumnRule r = rulesByCol.get(colField);
            if (r == null) continue; // parano
            boolean enabled = safeTest(r.enabledWhen(), vp);
            applier.setColumnEnabled(colField, enabled);
        }
    }

    private static boolean safeTest(Condition c, ValueProvider vp) {
        try {
            return c.test(vp);
        } catch (RuntimeException ex) {
            // fallback sécurisé : en cas d'erreur d'évaluation, désactive la colonne
            return false;
        }
    }
}
