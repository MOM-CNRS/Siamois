package fr.siamois.ui.api.openapi.v1.request;

import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;

import java.util.Map;

/**
 * The form part of an entity's PATCH: answers by field id, a new identifier, a new validation status
 * — each absent when the request leaves it alone.
 */
public interface FormPatchRequest {

    /**
     * The answers to write, by field id.
     *
     * @return the answers, possibly null or empty
     */
    Map<String, AnswerInput> getAnswers();

    /**
     * The requested identifier.
     *
     * @return the identifier, null when unchanged
     */
    String getIdentifier();

    /**
     * The requested validation status.
     *
     * @return the status, null when unchanged
     */
    ValidationStatus getValidationStatus();
}
