package fr.siamois.ui.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;

/**
 * Associe une colonne (identifiée par son CustomField) à sa condition enabledWhen.
 */
public record ColumnRule(CustomField columnField, Condition enabledWhen) {
    public ColumnRule {
        if (columnField == null) throw new IllegalArgumentException("columnField must not be null");
        if (enabledWhen == null) throw new IllegalArgumentException("enabledWhen must not be null");
    }
}
