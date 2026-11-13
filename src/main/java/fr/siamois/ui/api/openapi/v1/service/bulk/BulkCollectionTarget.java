package fr.siamois.ui.api.openapi.v1.service.bulk;

/**
 * A collection a kind of bulk action applies to, picked by its REST path segment.
 */
public interface BulkCollectionTarget {

    /**
     * The REST collection segment of the elements ("recording-units", "finds"…).
     *
     * @return the collection's path segment
     */
    String collection();
}
