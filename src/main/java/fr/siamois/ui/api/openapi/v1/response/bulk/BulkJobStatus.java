package fr.siamois.ui.api.openapi.v1.response.bulk;

/**
 * Where a bulk action running in the background stands.
 */
public enum BulkJobStatus {
    /** Elements are still being processed. */
    RUNNING,
    /** Every element was processed (each one succeeded or failed on its own). */
    DONE,
    /** The action stopped before the end, on an unexpected error. */
    FAILED,
    /** Its owner stopped the action: the elements processed until then stay changed. */
    CANCELLED
}
