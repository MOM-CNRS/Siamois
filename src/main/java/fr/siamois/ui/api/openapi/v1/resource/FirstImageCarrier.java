package fr.siamois.ui.api.openapi.v1.resource;

/**
 * A list row that can show the first image document linked to it (a card's picture, in the front's
 * « Vignettes » list view) — filled for a whole page at once by
 * {@link fr.siamois.ui.api.openapi.v1.service.FirstImageService}.
 */
public interface FirstImageCarrier {

    String getId();

    void setFirstImageDocumentId(String documentId);
}
