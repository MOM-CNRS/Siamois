package fr.siamois.ui.api.openapi.v1.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A list row that can show the first image document linked to it (a card's picture, in the front's
 * « Vignettes » list view) — filled for a whole page at once by
 * {@link fr.siamois.ui.api.openapi.v1.service.FirstImageService}. The JSON contract of the property is
 * declared here, once, for every resource that carries it.
 */
public interface FirstImageCarrier {

    String getId();

    @Schema(description = "Identifiant du premier document image lié (listes uniquement) : l'image des vignettes. "
            + "Absent quand aucun document image n'est lié.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String getFirstImageDocumentId();

    void setFirstImageDocumentId(String documentId);
}
