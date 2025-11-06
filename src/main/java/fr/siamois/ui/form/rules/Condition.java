package fr.siamois.ui.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;

import java.util.Set;

/** Contrat d'une condition enabledWhen. */
public interface Condition {
    /** Évalue la condition dans le contexte courant (valeurs des champs via ValueProvider). */
    boolean test(ValueProvider vp);

    /** Champs dont dépend la condition (pour invalidation ciblée). */
    Set<CustomField> dependsOn();
}

