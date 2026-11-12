package fr.siamois.ui.api.openapi.v1.service.utils;

/**
 * What a form PATCH changes, once its rights are checked and its identifier applied.
 *
 * @param answersChange    the request writes answers
 * @param identifierChange the request renames the entity (already written on it)
 * @param statusChange     the request moves the entity to another validation status
 */
public record FormPatchChanges(boolean answersChange, boolean identifierChange, boolean statusChange) {

    /**
     * Whether the entity's content must be saved: anything but a status change alone.
     *
     * @return true when the form is to be saved
     */
    public boolean writesContent() {
        return answersChange || identifierChange || !statusChange;
    }
}
