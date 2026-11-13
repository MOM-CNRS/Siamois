package fr.siamois.ui.api.openapi.v1.service.utils;

/**
 * What the caller may do to the entity a form PATCH targets.
 *
 * @param canEdit     the caller may edit the entity's content
 * @param canValidate the caller may move the entity to or from the validated status
 */
public record FormPatchRights(boolean canEdit, boolean canValidate) {
}
