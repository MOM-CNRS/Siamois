package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.domain.models.exceptions.HierarchyCycleException;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.identifier.ProjectIdentifierConstraints;
import fr.siamois.domain.services.identifier.IdentifierRangeExhaustedException;
import fr.siamois.ui.api.openapi.v1.exception.IdentifierConflictException;
import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Turns the failure of one element of a bulk action into what the unit call would have answered —
 * the same status and message as {@code RestExceptionHandler} gives them — so the bulk result can
 * name it without failing the whole request.
 */
@Slf4j
@Component
public class BulkFailureTranslator {

    private static final String GENERIC_MESSAGE = "Erreur";
    private static final String FORBIDDEN_MESSAGE = "Accès refusé";
    private static final String CONFLICT_MESSAGE = "Conflit avec l'état actuel de l'élément";
    private static final String INTERNAL_MESSAGE = "Erreur interne";

    /**
     * The failure of element {@code id}.
     *
     * @param id    the element's id
     * @param error what its unit operation threw
     * @return the element's failure: status and readable message
     */
    public BulkFailureResource translate(long id, RuntimeException error) {
        if (error instanceof ResponseStatusException statusError) {
            return new BulkFailureResource(id, statusError.getStatusCode().value(), orFallback(statusError.getReason(), GENERIC_MESSAGE));
        }
        if (isForbidden(error)) {
            return new BulkFailureResource(id, HttpStatus.FORBIDDEN.value(), FORBIDDEN_MESSAGE);
        }
        if (isNotFound(error)) {
            return new BulkFailureResource(id, HttpStatus.NOT_FOUND.value(), error.getMessage());
        }
        if (isConflict(error)) {
            return new BulkFailureResource(id, HttpStatus.CONFLICT.value(), orFallback(error.getMessage(), CONFLICT_MESSAGE));
        }
        return unexpected(id, error);
    }

    /**
     * Whether the caller may not do this to the element.
     */
    private static boolean isForbidden(RuntimeException error) {
        return error instanceof AccessDeniedException || error instanceof ForbiddenOperationException;
    }

    /**
     * Whether the element does not exist, or not for the caller.
     */
    private static boolean isNotFound(RuntimeException error) {
        return error instanceof RecordingUnitNotFoundException || error instanceof ActionUnitNotFoundException;
    }

    /**
     * Whether the element's current state forbids the change (identifier taken, hierarchy loop, concurrent edit).
     */
    private static boolean isConflict(RuntimeException error) {
        return error instanceof IdentifierConflictException
                || error instanceof HierarchyCycleException
                || error instanceof IdentifierRangeExhaustedException
                || error instanceof SyncRevisionConflictException
                || ProjectIdentifierConstraints.isViolatedBy(error);
    }

    /**
     * The message an error carries, or the fallback when it has none.
     */
    private static String orFallback(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }

    /**
     * An error nothing expected: logged with a correlation id, reported as an internal error.
     */
    private static BulkFailureResource unexpected(long id, RuntimeException error) {
        String correlationId = UUID.randomUUID().toString();
        log.error("[{}] Unhandled exception in bulk action on element {}", correlationId, id, error);
        return new BulkFailureResource(id, HttpStatus.INTERNAL_SERVER_ERROR.value(), INTERNAL_MESSAGE + " (" + correlationId + ")");
    }
}
