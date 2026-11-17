package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The recording units on a map, by their own geometry (the system field bound to {@code geom}) or by
 * an additional geometry field: the geometries come from PostGIS, the identifier, type and validation
 * status from the same mapper as the list's rows.
 */
@Component
@RequiredArgsConstructor
public class RecordingUnitMapFeatureSource implements MapFeatureSource<RecordingUnitDTO> {

    /** The unit's own geometry column. */
    static final long OWN_GEOMETRY = SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "geom").getId();

    private final RecordingUnitRepository recordingUnitRepository;
    private final RecordingUnitResponseMapper recordingUnitResponseMapper;
    private final CustomFieldRepository customFieldRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Long idOf(RecordingUnitDTO row) {
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
        return geometryFieldId == OWN_GEOMETRY ? RecordingUnitSpec.hasGeometry() : RecordingUnitSpec.hasTextAnswerTo(geometryFieldId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object[]> geoJsonWgs84ByIds(Collection<Long> ids, long geometryFieldId) {
        return geometryFieldId == OWN_GEOMETRY
                ? recordingUnitRepository.findGeoJsonWgs84ByIds(ids)
                : recordingUnitRepository.findAnswerGeoJsonWgs84ByIds(ids, geometryFieldId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityMapResponse.Feature toFeature(RecordingUnitDTO row, JsonNode geometry) {
        RecordingUnitResource unit = Objects.requireNonNull(recordingUnitResponseMapper.convert(row));
        return new EntityMapResponse.Feature(
                unit.getId(), unit.getFullIdentifier(), unit.getType(), unit.getValidationStatus(), geometry);
    }
}
