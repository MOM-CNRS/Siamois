package fr.siamois.domain.models.settings.tableconfig;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * Each constant is spelled exactly like the {@code @DiscriminatorValue} of the matching
 * {@code fr.siamois.domain.models.form.customfield.CustomField} subclass (the {@code answer_type}
 * discriminator column), so this taxonomy stays in lockstep with the persisted field types. The one
 * folding is the pair {@code SELECT_ONE_FROM_FIELD_CODE} / {@code SELECT_MULTIPLE_FROM_FIELD_CODE},
 * which the screen shows as {@link #SELECT_ONE} / {@link #SELECT_MULTIPLE}: both are a controlled
 * vocabulary, they only differ by where the list of values comes from.
 * <p>
 * Not every type can be created from the field configuration screen: see {@link #isUserCreatable()}.
 * A type that is not creatable only ever comes from the system fields of a form.
 * <p>
 * "Multiple values" is not a separate flag: it is the choice of the multiple-valued sibling of a
 * type ({@link #multipleVariant()}), the one that stores a list of references.
 */
@Getter
public enum FieldType {
    TEXT("Texte", "bi bi-fonts", true),
    INTEGER("Entier", "bi bi-123", true),
    DECIMAL("Nombre décimal", "bi bi-123", true),
    DATETIME("Date", "bi bi-calendar-event", true),
    BOOLEAN("Oui / non", "bi bi-ui-checks", true),
    MEASUREMENT("Mesure", "bi bi-rulers", true),
    FILE("Fichier", "bi bi-file-earmark", false),
    SELECT_ONE("Vocabulaire contrôlé", "sia-icon-opentheso", true),
    SELECT_MULTIPLE("Vocabulaire contrôlé (plusieurs valeurs)", "sia-icon-opentheso", true),
    SELECT_ONE_PERSON("Personne", "bi bi-person", false),
    SELECT_MULTIPLE_PERSON("Personnes", "bi bi-people", false),
    SELECT_ONE_SPATIAL_UNIT("Lieu", "bi bi-geo-alt", false),
    SELECT_MULTIPLE_SPATIAL_UNIT("Lieux", "bi bi-geo-alt", false),
    SELECT_MULTIPLE_SPATIAL_UNIT_TREE("Lieux (arbre)", "bi bi-diagram-3", false),
    SELECT_ADDRESS("Adresse", "bi bi-signpost", false),
    SELECT_ONE_ACTION_UNIT("Projet", "bi bi-arrow-down-square", false),
    SELECT_ONE_RECORDING_UNIT("Unité d'enregistrement", "bi bi-pencil-square", false),
    SELECT_MULTIPLE_RECORDING_UNIT("Unités d'enregistrement", "bi bi-pencil-square", false),
    SELECT_MULTIPLE_SPECIMEN("Mobilier", "bi bi-box", false),
    SELECT_MULTIPLE_CONTAINER("Contenants", "bi bi-archive", false),
    SELECT_MULTIPLE_PHASE("Phases", "bi bi-layers", false),
    SELECT_MULTIPLE_STRATIGRAPHY("Relations stratigraphiques", "bi bi-diagram-2", false);

    private final String label;
    /** The CSS classes of the icon that stands for this type of field, in front of its name (Bootstrap, or the OpenTheso "O" for a vocabulary). */
    private final String icon;
    /** Whether a project can pick this type when it adds a field of its own. */
    private final boolean userCreatable;

    FieldType(String label, String icon, boolean userCreatable) {
        this.label = label;
        this.icon = icon;
        this.userCreatable = userCreatable;
    }

    public boolean isConfigurable() {
        return this == SELECT_ONE || this == SELECT_MULTIPLE;
    }

    /** Whether the type stores several values (a list of references) rather than one. */
    public boolean isMultiple() {
        return name().startsWith("SELECT_MULTIPLE");
    }

    /** Whether the type has a multiple-valued sibling, so the screen can offer "multiple values". */
    public boolean supportsMultiple() {
        return !isMultiple() && multipleVariant() != this;
    }

    /** The multiple-valued sibling of this type, or the type itself when it has none. */
    public FieldType multipleVariant() {
        return switch (this) {
            case SELECT_ONE -> SELECT_MULTIPLE;
            case SELECT_ONE_PERSON -> SELECT_MULTIPLE_PERSON;
            case SELECT_ONE_SPATIAL_UNIT -> SELECT_MULTIPLE_SPATIAL_UNIT;
            case SELECT_ONE_RECORDING_UNIT -> SELECT_MULTIPLE_RECORDING_UNIT;
            default -> this;
        };
    }

    /** The single-valued sibling of this type, or the type itself when it has none. */
    public FieldType singleVariant() {
        return switch (this) {
            case SELECT_MULTIPLE -> SELECT_ONE;
            case SELECT_MULTIPLE_PERSON -> SELECT_ONE_PERSON;
            case SELECT_MULTIPLE_SPATIAL_UNIT -> SELECT_ONE_SPATIAL_UNIT;
            case SELECT_MULTIPLE_RECORDING_UNIT -> SELECT_ONE_RECORDING_UNIT;
            default -> this;
        };
    }

    /** The type this one shows as when the screen offers a single entry for the one/multiple pair. */
    public FieldType family() {
        return singleVariant();
    }

    /** The types a project can pick when it adds a field, in the order the screen lists them. */
    public static List<FieldType> userCreatableTypes() {
        return Arrays.stream(values()).filter(FieldType::isUserCreatable).toList();
    }

    /**
     * The type for a persisted {@code answer_type}. {@code SELECT_*_FROM_FIELD_CODE} is the same
     * type as {@code SELECT_*}.
     *
     * @throws IllegalArgumentException when the discriminator is not a known field type
     */
    public static FieldType fromDiscriminator(String discriminator) {
        return switch (discriminator) {
            case "SELECT_ONE_FROM_FIELD_CODE" -> SELECT_ONE;
            case "SELECT_MULTIPLE_FROM_FIELD_CODE" -> SELECT_MULTIPLE;
            default -> valueOf(discriminator);
        };
    }
}
