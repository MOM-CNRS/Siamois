package fr.siamois.ui.api.openapi.v1.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMapResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Builds the map view of a recording-unit list: walks the very same filtered, authorized query the
 * list uses (so search, f.* filters and project visibility are inherited, not re-implemented),
 * then asks PostGIS for those units' geometries in WGS84.
 */
@Service
@RequiredArgsConstructor
public class RecordingUnitMapService {

    /** Same as the list's page size cap — one page per query. */
    public static final int PAGE_SIZE = ProjectApiService.MAX_PAGE_SIZE;
    /** What a map shows at most; beyond it the response says so ({@code meta.truncated}). */
    public static final int MAX_FEATURES = 2000;

    private final RecordingUnitRepository recordingUnitRepository;
    private final RecordingUnitResponseMapper recordingUnitResponseMapper;
    private final ObjectMapper objectMapper;

    /** @param pageAt the list's query for the page starting at the given offset (limit {@link #PAGE_SIZE}) */
    @Transactional(readOnly = true)
    public RecordingUnitMapResponse build(IntFunction<Page<RecordingUnitDTO>> pageAt) {
        List<RecordingUnitResource> units = new ArrayList<>();
        long total = 0;
        for (int offset = 0; units.size() < MAX_FEATURES; offset += PAGE_SIZE) {
            Page<RecordingUnitDTO> page = pageAt.apply(offset);
            total = page.getTotalElements();
            page.getContent().forEach(dto -> units.add(recordingUnitResponseMapper.convert(dto)));
            if (!page.hasNext()) break;
        }
        boolean truncated = total > units.size();

        Map<String, JsonNode> geometries = geometriesById(units);
        List<RecordingUnitMapResponse.Feature> features = units.stream()
                .filter(u -> geometries.containsKey(u.getId()))
                .map(u -> new RecordingUnitMapResponse.Feature(
                        u.getId(), u.getFullIdentifier(), u.getType(), u.getValidated(), geometries.get(u.getId())))
                .toList();
        return new RecordingUnitMapResponse(features,
                new RecordingUnitMapResponse.Meta(total, features.size(), units.size() - features.size(), truncated));
    }

    private Map<String, JsonNode> geometriesById(List<RecordingUnitResource> units) {
        Map<String, JsonNode> byId = new HashMap<>();
        if (units.isEmpty()) return byId;
        List<Long> ids = units.stream().map(u -> Long.valueOf(u.getId())).toList();
        for (Object[] row : recordingUnitRepository.findGeoJsonWgs84ByIds(ids)) {
            try {
                byId.put(String.valueOf(row[0]), objectMapper.readTree((String) row[1]));
            } catch (JsonProcessingException e) {
                // A geometry PostGIS can't serialize is left off the map, like one with no SRID.
            }
        }
        return byId;
    }
}
