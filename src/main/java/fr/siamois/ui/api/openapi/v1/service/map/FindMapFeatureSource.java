package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The finds on a map, by their own geometry (the system field bound to {@code geom}, usually a point) or
 * by an additional geometry field: the geometries come from PostGIS, the identifier, type and validation
 * status from the same mapper as the list's rows.
 */
@Component
@RequiredArgsConstructor
public class FindMapFeatureSource implements MapFeatureSource<SpecimenDTO> {

    /** The find's own geometry column. */
    static final long OWN_GEOMETRY = SystemFieldCatalog.fieldBoundTo(ConfigurableTable.MOBILIER, "geom").getId();

    private final SpecimenRepository specimenRepository;
    private final FindOpenApiMapper findOpenApiMapper;
    private final CustomFieldRepository customFieldRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Long idOf(SpecimenDTO row) {
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
        return MapGeometryColumns.isGeometryColumn(OWN_GEOMETRY, fieldId, customFieldRepository);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Specification<?> withGeometry(long geometryFieldId) {
        return geometryFieldId == OWN_GEOMETRY ? SpecimenSpec.hasGeometry() : SpecimenSpec.hasTextAnswerTo(geometryFieldId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object[]> geoJsonWgs84ByIds(Collection<Long> ids, long geometryFieldId) {
        return geometryFieldId == OWN_GEOMETRY
                ? specimenRepository.findGeoJsonWgs84ByIds(ids)
                : specimenRepository.findAnswerGeoJsonWgs84ByIds(ids, geometryFieldId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityMapResponse.Feature toFeature(SpecimenDTO row, JsonNode geometry) {
        FindResource find = Objects.requireNonNull(findOpenApiMapper.toResource(row));
        return new EntityMapResponse.Feature(
                find.getId(), find.getFullIdentifier(), find.getType(), find.getValidationStatus(), geometry);
    }
}
