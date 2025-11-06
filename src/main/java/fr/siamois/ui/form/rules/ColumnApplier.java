package fr.siamois.ui.form.rules;


import fr.siamois.domain.models.form.customfield.CustomField;

/** Pont pour appliquer l'état enabled/disabled à la UI/state. */
@FunctionalInterface
public interface ColumnApplier {
    void setColumnEnabled(CustomField columnField, boolean enabled);
}

