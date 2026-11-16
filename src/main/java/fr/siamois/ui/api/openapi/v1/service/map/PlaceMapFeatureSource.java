package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.SpatialUnitSpec;
import fr.siamois.ui.api.openapi.v1.mapper.PlaceOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The places on a map, by their own geometry (the system field bound to {@code geom}) — the place form
 * is fixed, so there is no additional geometry field to place them by. The geometries come from PostGIS,
 * the name, category and validation status from the same mapper as the list's rows.
 */
@Component
@RequiredArgsConstructor
public class PlaceMapFeatureSource implements MapFeatureSource<SpatialUnitDTO> {

    /** The place's own geometry column. */
    static final long OWN_GEOMETRY = SpatialUnit.GEOM_FIELD.getId();

    private final SpatialUnitRepository spatialUnitRepository;
    private final PlaceOpenApiMapper placeOpenApiMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Long idOf(SpatialUnitDTO row) {
        return row.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long defaultGeometryFieldId() {
        return OWN_GEOMETRY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isGeometryColumn(long fieldId) {
        return fieldId == OWN_GEOMETRY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Specification<?> withGeometry(long geometryFieldId) {
        return SpatialUnitSpec.hasGeometry();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object[]> geoJsonWgs84ByIds(Collection<Long> ids, long geometryFieldId) {
        return spatialUnitRepository.findGeoJsonWgs84ByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityMapResponse.Feature toFeature(SpatialUnitDTO row, JsonNode geometry) {
        // No language: the category reads in the default one, like the recording units' type.
        PlaceResource place = Objects.requireNonNull(placeOpenApiMapper.toResource(row, null));
        return new EntityMapResponse.Feature(
                place.getId(), place.getName(), place.getType(), place.getValidationStatus(), geometry);
    }
}
