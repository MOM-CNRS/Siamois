package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapFeatureResponse;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
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
 * Builds the map view of any list: walks the very same filtered, authorized query the list uses (so
 * search, f.* filters and visibility are inherited, not re-implemented), then asks the collection's
 * {@link MapFeatureSource} for those rows' geometries in WGS84. Only what the server alone knows is
 * answered; counting what is left out and styling the features is the client's job.
 */
@Service
@RequiredArgsConstructor
public class EntityMapService {

    /** Same as the list's page size cap — one page per query. */
    public static final int PAGE_SIZE = ProjectApiService.MAX_PAGE_SIZE;
    /** What a map examines at most; beyond it {@code meta.total} exceeds {@code meta.examined}. */
    public static final int MAX_FEATURES = 2000;

    private final ObjectMapper objectMapper;

    /**
     * The rows of the list, up to {@link #MAX_FEATURES}, with those that can be placed on a map.
     *
     * @param pageAt the list's query for the page starting at the given offset (limit {@link #PAGE_SIZE})
     * @param source the collection's geometries and feature shape
     * @param <D>    the row type of the list
     * @return the placed rows and how many were examined
     */
    @Transactional(readOnly = true)
    public <D> EntityMapResponse build(IntFunction<Page<D>> pageAt, MapFeatureSource<D> source) {
        List<D> rows = new ArrayList<>();
        long total = 0;
        for (int offset = 0; rows.size() < MAX_FEATURES; offset += PAGE_SIZE) {
            Page<D> page = pageAt.apply(offset);
            total = page.getTotalElements();
            rows.addAll(page.getContent());
            if (!page.hasNext()) break;
        }

        Map<Long, JsonNode> geometries = geometriesById(rows, source);
        List<EntityMapResponse.Feature> features = rows.stream()
                .filter(row -> geometries.containsKey(source.idOf(row)))
                .map(row -> source.toFeature(row, geometries.get(source.idOf(row))))
                .toList();
        return new EntityMapResponse(features, new EntityMapResponse.Meta(total, rows.size()));
    }

    /**
     * One row on a map, outside of its list: the fiche shown beside a map whose filter left it out.
     *
     * @param row    the row, already checked visible by the caller
     * @param source the collection's geometries and feature shape
     * @param <D>    the row type of the collection
     * @return the row's feature, null when it can't be placed (no geometry, no or unknown SRID)
     */
    @Transactional(readOnly = true)
    public <D> EntityMapFeatureResponse feature(D row, MapFeatureSource<D> source) {
        JsonNode geometry = geometriesById(List.of(row), source).get(source.idOf(row));
        return new EntityMapFeatureResponse(geometry == null ? null : source.toFeature(row, geometry));
    }

    /**
     * The GeoJSON geometry in WGS84 of each row that has a usable one, by row id.
     */
    private <D> Map<Long, JsonNode> geometriesById(List<D> rows, MapFeatureSource<D> source) {
        Map<Long, JsonNode> byId = new HashMap<>();
        if (rows.isEmpty()) return byId;
        for (Object[] row : source.geoJsonWgs84ByIds(rows.stream().map(source::idOf).toList())) {
            try {
                byId.put(((Number) row[0]).longValue(), objectMapper.readTree((String) row[1]));
            } catch (JsonProcessingException e) {
                // A geometry PostGIS can't serialize is left off the map, like one with no SRID.
            }
        }
        return byId;
    }
}
