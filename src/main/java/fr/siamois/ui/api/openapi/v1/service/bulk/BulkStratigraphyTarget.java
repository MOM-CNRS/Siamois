package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;

/**
 * A collection whose elements take stratigraphic relationships in bulk: each one goes through the
 * same service call (rights and rules) as the unit POST / DELETE of its relationships.
 */
public interface BulkStratigraphyTarget {

    /**
     * The REST collection segment of the elements ("recording-units").
     *
     * @return the collection's path segment
     */
    String collection();

    /**
     * Adds the relationship to one element.
     *
     * @param caller       the authenticated caller
     * @param id           the element's numeric id
     * @param relationship the relationship to add, from this element to the other unit
     */
    void addStratigraphy(ProjectApiCaller caller, long id, RecordingUnitStratigraphicRelationshipRequest relationship);

    /**
     * Removes the relationship between one element and the other unit.
     *
     * @param caller    the authenticated caller
     * @param id        the element's numeric id
     * @param relatedId the other unit's numeric id
     */
    void removeStratigraphy(ProjectApiCaller caller, long id, long relatedId);
}
