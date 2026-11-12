package fr.siamois.ui.api.openapi.v1.service.utils;

import fr.siamois.dto.entity.ProjectIdentifiedDTO;
import fr.siamois.ui.api.openapi.v1.request.FormPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.IdentifierPatch;
import fr.siamois.ui.api.openapi.v1.service.ValidationOpenApiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.BooleanSupplier;

public final class OpenApiServiceUtils {

    private OpenApiServiceUtils() {
        throw new UnsupportedOperationException("OpenApiServiceUtils should not be instantiated");
    }

    /**
     * The checks every form PATCH of a project entity starts with, the same for phases, containers,
     * studies and documents: a validator may change the status alone without the edit right, anything
     * else needs it, and the status transition its own rights (403 otherwise). A new identifier is then
     * written on the entity and checked to be free in its project (409 otherwise).
     *
     * @param entity          the entity being patched
     * @param request         the form part of the PATCH
     * @param rights          what the caller may do to the entity
     * @param validation      the rules of validation status transitions
     * @param identifierTaken whether the entity's identifier, once renamed, is already used in its project
     * @return what the PATCH changes, for the caller to save what it must
     */
    public static FormPatchChanges applyFormPatch(ProjectIdentifiedDTO entity,
                                                  FormPatchRequest request,
                                                  FormPatchRights rights,
                                                  ValidationOpenApiService validation,
                                                  BooleanSupplier identifierTaken) {
        String newIdentifier = IdentifierPatch.requested(request.getIdentifier());
        FormPatchChanges changes = new FormPatchChanges(
                request.getAnswers() != null && !request.getAnswers().isEmpty(),
                IdentifierPatch.changes(newIdentifier, entity.getIdentifier()),
                ValidationOpenApiService.changes(entity.getValidationStatus(), request.getValidationStatus()));
        if (changes.writesContent() && !rights.canEdit()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
        validation.requireAllowed(entity.getValidationStatus(), request.getValidationStatus(),
                rights.canEdit(), rights.canValidate());
        if (changes.identifierChange()) {
            entity.setIdentifier(newIdentifier);
            IdentifierPatch.requireFree(identifierTaken.getAsBoolean());
        }
        return changes;
    }
}
