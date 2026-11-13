package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;

import java.util.Set;

/**
 * What a bulk action does to ONE element of a collection: the same rights, rules and transaction
 * as the collection's unit DELETE / PATCH, so a bulk action never bypasses them. One implementation
 * per collection, picked by {@link #collection()}.
 */
public interface BulkEntityOperations extends BulkCollectionTarget {

    /**
     * Deletes one element, as DELETE /{collection}/{id} does — except that its links to the other
     * elements of the selection don't stop it: they go with it.
     *
     * @param caller    the authenticated caller
     * @param id        the element's numeric id
     * @param selection the ids of every element the bulk deletion deletes
     * @param lang      the request's language
     */
    void delete(ProjectApiCaller caller, long id, Set<Long> selection, String lang);

    /**
     * Applies a partial update to one element, as PATCH /{collection}/{id} does.
     *
     * @param caller the authenticated caller
     * @param id     the element's numeric id
     * @param patch  the unit PATCH body of the collection
     * @param lang   the request's language
     */
    void patch(ProjectApiCaller caller, long id, JsonNode patch, String lang);
}
