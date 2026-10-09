package fr.siamois.domain.models.form.config;

/**
 * How a vocabulary field's list relates to its source (field code, thesaurus branch or collection).
 */
public enum VocabularyMode {

    /**
     * The list follows the source: any concept the thesaurus adds is offered, any it removes is dropped.
     * Concepts can still be switched off one by one ({@link ConceptState#DISABLED}). The only mode
     * a field had before modes existed, hence the default.
     */
    FOLLOW,

    /**
     * The list is the set of concepts explicitly {@link ConceptState#ENABLED}: the source only seeded it.
     * A concept the thesaurus adds afterwards waits as {@link ConceptState#PENDING}.
     */
    FROZEN
}
