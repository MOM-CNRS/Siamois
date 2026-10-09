package fr.siamois.domain.services.placesource;

import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An external source of place suggestions (INSEE communes, GéoPlateforme addresses…). A source is a
 * Spring bean: adding one is adding a class, with no change to the endpoints or to the client, which
 * only knows the source by its id.
 */
public interface PlaceSourceProvider {

    /** The id forms and rules refer to the source by (INSEE, GEOPLAT…). */
    String id();

    /** The parameters a form may bind to the value of another field to narrow {@link #search}. */
    Set<String> declaredParams();

    /** The suggestions for {@code query}, narrowed by {@code params} (only declared ones are given). */
    List<ExternalPlace> search(String query, Map<String, String> params);

    /** The type of the places this source creates. */
    ConceptDTO category();

    /** The place to create when a suggestion is picked. */
    SpatialUnitDTO draftOf(ExternalPlace place);

    /** Whether picking {@code place} twice means one place: by code when the source has one, else by name. */
    default boolean identifiedByCode() {
        return false;
    }
}
