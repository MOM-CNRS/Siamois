package fr.siamois.domain.models.form.config;

/**
 * The state of one concept in a vocabulary field's list ({@link ConceptFieldState}).
 */
public enum ConceptState {

    /** Offered in the field. */
    ENABLED,

    /** Switched off for this field: not offered, but still displayed where it is already an answer. */
    DISABLED,

    /** New in the source of a {@link VocabularyMode#FROZEN} field, not offered until someone enables it. */
    PENDING
}
