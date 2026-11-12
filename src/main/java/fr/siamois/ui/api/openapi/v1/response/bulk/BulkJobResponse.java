package fr.siamois.ui.api.openapi.v1.response.bulk;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import lombok.EqualsAndHashCode;

/**
 * Réponse d'une action en lot : {@code { data: BulkJobResource }}.
 */
@EqualsAndHashCode(callSuper = true)
public class BulkJobResponse extends Response<BulkJobResource> {

    /**
     * Enveloppe la tâche.
     *
     * @param data la tâche et son avancement
     */
    public BulkJobResponse(BulkJobResource data) {
        super(data);
    }
}
