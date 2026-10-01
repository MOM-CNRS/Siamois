package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.services.ValidationStatusService;
import fr.siamois.dto.entity.PersonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The {@code validated} property of every entity PATCH (project, recording unit, find, phase,
 * container, place): the rights rule, then the write. Each patch service calls
 * {@link #requireAllowed} with the rights it already computes, before touching anything, and
 * {@link #apply} after its own save — the services' {@code save(dto)} copies the DTO's status onto
 * the entity, so applying it earlier would be overwritten.
 *
 * <p>Rule: any editor may move between en cours / terminé / annulé; reaching "validé", or leaving it,
 * takes the validator right ({@link ValidationStatus#requiresValidatorTo}) — which is enough on its
 * own: a validator without the edit right may still validate.</p>
 */
@Service
@RequiredArgsConstructor
public class ValidationOpenApiService {

    private final ValidationStatusService validationStatusService;

    /** Whether the patch asks for an actual status change. */
    public static boolean changes(ValidationStatus current, ValidationStatus requested) {
        return requested != null && requested != current;
    }

    /**
     * 403 unless the caller may move the entity from {@code current} to {@code requested}.
     * No-op when the patch doesn't change the status.
     */
    public void requireAllowed(ValidationStatus current, ValidationStatus requested, boolean canEdit, boolean canValidate) {
        ValidationStatus from = current != null ? current : ValidationStatus.INCOMPLETE;
        if (!changes(from, requested)) {
            return;
        }
        boolean needsValidator = from.requiresValidatorTo(requested);
        if (needsValidator ? !canValidate : !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, needsValidator
                    ? "Seul un validateur peut valider ou dévalider cette fiche"
                    : "Modification non autorisée");
        }
    }

    /** Writes the requested status (and who validated/when). No-op when {@code requested} is null. */
    public void apply(Class<? extends TraceableEntity> entityType, long id, ValidationStatus requested, PersonDTO person) {
        if (requested == null) {
            return;
        }
        validationStatusService.setStatus(entityType, id, requested, person != null ? person.getId() : null);
    }
}
