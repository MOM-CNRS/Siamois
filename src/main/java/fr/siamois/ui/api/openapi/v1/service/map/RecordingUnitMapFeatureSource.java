package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The recording units on a map: their geometries come from PostGIS, their identifier, type and
 * validation status from the same mapper as the list's rows.
 */
@Component
@RequiredArgsConstructor
public class RecordingUnitMapFeatureSource implements MapFeatureSource<RecordingUnitDTO> {

    private final RecordingUnitRepository recordingUnitRepository;
    private final RecordingUnitResponseMapper recordingUnitResponseMapper;

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
    public List<Object[]> geoJsonWgs84ByIds(Collection<Long> ids) {
        return recordingUnitRepository.findGeoJsonWgs84ByIds(ids);
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
