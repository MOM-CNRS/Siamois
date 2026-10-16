package fr.siamois.domain.models.document;

/**
 * Lifecycle of one file row inside a bulk import draft.
 */
public enum DocumentImportItemStatus {
    /** Metadata still being edited; not yet promoted to a Document. */
    DRAFT,
    /** Last submit attempt failed; error_message holds the reason. */
    ERROR
}
