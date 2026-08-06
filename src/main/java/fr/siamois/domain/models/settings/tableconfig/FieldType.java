package fr.siamois.domain.models.settings.tableconfig;

import lombok.Getter;

/**
 * Except for {@code PROJET}, each constant is spelled exactly like the matching
 * {@code @DiscriminatorValue} on a {@code fr.siamois.domain.models.form.customfield.CustomField}
 * subclass (the {@code answer_type} discriminator column), so this UI-only taxonomy stays in
 * lockstep with the real persisted field types. {@code PROJET} has no {@code CustomField}
 * discriminator yet; it's system-field-only (never user-creatable) so that's not a problem today.
 * The real model has several more entity-specific discriminators (e.g. {@code SELECT_ONE_PERSON})
 * that aren't exposed here, as well as no equivalent yet for a typology or a generic
 * parent/child relation — those are intentionally left out until they're modeled on the real side.
 */
@Getter
public enum FieldType {
    TEXT("Texte"),
    INTEGER("Numérique"),
    MEASUREMENT("Mesure"),
    SELECT_ONE("Vocabulaire contrôlé"),
    SELECT_MULTIPLE("Vocabulaire contrôlé (plusieurs valeurs)"),
    SELECT_ONE_RECORDING_UNIT("Unité d'enregistrement"),
    SELECT_ONE_SPATIAL_UNIT("Lieu"),
    PROJET("Projet");

    private final String label;

    FieldType(String label) {
        this.label = label;
    }

    public boolean isConfigurable() {
        return this == SELECT_ONE || this == SELECT_MULTIPLE;
    }
}
