package fr.siamois.ui.api.openapi.v1.response.bulk;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import lombok.EqualsAndHashCode;

/**
 * Réponse d'une action en lot : {@code { data: BulkResultResource }}.
 */
@EqualsAndHashCode(callSuper = true)
public class BulkResultResponse extends Response<BulkResultResource> {

    /**
     * Enveloppe le bilan.
     *
     * @param data le bilan de l'action
     */
    public BulkResultResponse(BulkResultResource data) {
        super(data);
    }
}
