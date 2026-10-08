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
    TEXT("Texte", "bi-fonts"),
    INTEGER("Numérique", "bi-123"),
    MEASUREMENT("Mesure", "bi-rulers"),
    SELECT_ONE("Vocabulaire contrôlé", "bi-ui-radios"),
    SELECT_MULTIPLE("Vocabulaire contrôlé (plusieurs valeurs)", "bi-ui-checks"),
    SELECT_ONE_RECORDING_UNIT("Unité d'enregistrement", "bi-pencil-square"),
    SELECT_ONE_SPATIAL_UNIT("Lieu", "bi-geo-alt"),
    PROJET("Projet", "bi-arrow-down-square");

    private final String label;
    /** The Bootstrap icon that stands for this type of field, in front of the field's name. */
    private final String icon;

    FieldType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public boolean isConfigurable() {
        return this == SELECT_ONE || this == SELECT_MULTIPLE;
    }
}
