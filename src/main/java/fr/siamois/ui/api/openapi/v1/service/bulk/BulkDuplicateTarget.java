package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;

/**
 * A collection whose elements can be duplicated in bulk: each one goes through the same service call
 * (rights, rules, regenerated identifier) as its unit POST /{collection}/{id}/duplicate. The copy is the
 * element alone: its hierarchy (parents, children) and its relationships are not duplicated.
 */
public interface BulkDuplicateTarget extends BulkCollectionTarget {

    /**
     * Duplicates one element.
     *
     * @param caller the authenticated caller
     * @param id     the element's numeric id
     * @param lang   the request's language
     */
    void duplicate(ProjectApiCaller caller, long id, String lang);
}
