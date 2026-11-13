package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;

/**
 * What a bulk action does to ONE element of a collection: the same rights, rules and transaction
 * as the collection's unit DELETE / PATCH, so a bulk action never bypasses them. One implementation
 * per collection, picked by {@link #collection()}.
 */
public interface BulkEntityOperations {

    /**
     * The REST collection segment these operations serve ("recording-units", "finds"…).
     *
     * @return the collection's path segment
     */
    String collection();

    /**
     * Deletes one element, as DELETE /{collection}/{id} does.
     *
     * @param caller         the authenticated caller
     * @param id             the element's numeric id
     * @param acceptLanguage the request's Accept-Language header (may be null)
     */
    void delete(ProjectApiCaller caller, long id, String acceptLanguage);

    /**
     * Applies a partial update to one element, as PATCH /{collection}/{id} does.
     *
     * @param caller         the authenticated caller
     * @param id             the element's numeric id
     * @param patch          the unit PATCH body of the collection
     * @param acceptLanguage the request's Accept-Language header (may be null)
     */
    void patch(ProjectApiCaller caller, long id, JsonNode patch, String acceptLanguage);
}
