package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;

/**
 * A collection documents can be linked to in bulk. Linking or unlinking a document changes the
 * element's relations, so a bulk link first checks, element by element, that the caller may edit it
 * (the unit link endpoint only checks the right on the document).
 */
public interface BulkDocumentLinkTarget extends BulkCollectionTarget {

    /**
     * Fails (403, or 404 when out of reach) unless the caller may edit the element.
     *
     * @param caller the authenticated caller
     * @param id     the element's numeric id
     * @param lang   the request's language
     */
    void requireEditable(ProjectApiCaller caller, long id, String lang);
}
