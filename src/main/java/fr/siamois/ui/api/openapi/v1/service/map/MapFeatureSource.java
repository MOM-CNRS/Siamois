package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;

import java.util.Collection;
import java.util.List;

/**
 * What a map view needs of ONE collection: its geometry columns, where its rows' geometries are by
 * each, and how a row reads as a feature. The rows themselves come from the collection's own list query, so its search, filters and
 * visibility are inherited, never re-implemented. One implementation per mappable collection.
 *
 * @param <D> the row type the collection's list query pages
 */
public interface MapFeatureSource<D> {

    /**
     * The row's numeric id.
     *
     * @param row a row of the list
     * @return its id
     */
    Long idOf(D row);

    /**
     * The geometry column the rows are placed by when none is asked for: their own geometry field.
     *
     * @return its field id
     */
    long defaultGeometryFieldId();

    /**
     * Whether a field is a geometry column of the collection: its own geometry field, or an additional
     * field of type geometry.
     *
     * @param fieldId a field id
     * @return true when the rows can be placed by it
     */
    boolean isGeometryColumn(long fieldId);

    /**
     * The geometry of each given row by a geometry column, as GeoJSON in WGS84, for those that have a usable one.
     *
     * @param ids             the rows' ids
     * @param geometryFieldId a geometry column ({@link #isGeometryColumn})
     * @return {@code [id, GeoJSON string]} pairs; a row without a placeable geometry is left out
     */
    List<Object[]> geoJsonWgs84ByIds(Collection<Long> ids, long geometryFieldId);

    /**
     * The row as a map feature, around a geometry already found for it.
     *
     * @param row      a row of the list
     * @param geometry its GeoJSON geometry in WGS84
     * @return the feature
     */
    EntityMapResponse.Feature toFeature(D row, JsonNode geometry);
}
