package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapFeatureResponse;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityMapServiceTest {

    private static final String POINT = "{\"type\":\"Point\",\"coordinates\":[2.35,48.85]}";

    /**
     * A collection whose rows are their own ids, placed when the given geometries have them.
     */
    private record FakeSource(Map<Long, String> geoJsonById) implements MapFeatureSource<Long> {

        /**
         * {@inheritDoc}
         */
        @Override
        public Long idOf(Long row) {
            return row;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<Object[]> geoJsonWgs84ByIds(Collection<Long> ids) {
            List<Object[]> rows = new ArrayList<>();
            ids.stream().filter(geoJsonById::containsKey).distinct()
                    .forEach(id -> rows.add(new Object[]{id, geoJsonById.get(id)}));
            return rows;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public EntityMapResponse.Feature toFeature(Long row, JsonNode geometry) {
            return new EntityMapResponse.Feature(String.valueOf(row), "UE-" + row, null, null, geometry);
        }
    }

    /**
     * The service under test.
     */
    private EntityMapService service() {
        return new EntityMapService(new ObjectMapper());
    }

    /**
     * A row with a geometry is placed, one without is only examined.
     */
    @Test
    void build_PlacesRowsWithAGeometryAndExaminesTheOthers() {
        FakeSource source = new FakeSource(Map.of(1L, POINT));

        EntityMapResponse response = service().build(offset ->
                new PageImpl<>(List.of(1L, 2L), PageRequest.of(0, EntityMapService.PAGE_SIZE), 2), source);

        assertEquals(1, response.features().size());
        assertEquals("1", response.features().get(0).id());
        assertEquals("UE-1", response.features().get(0).label());
        assertEquals("Point", response.features().get(0).geometry().get("type").asText());
        assertEquals(new EntityMapResponse.Meta(2, 2), response.meta());
    }

    /**
     * The pages are walked up to the cap, and the response says how many of the total were examined.
     */
    @Test
    void build_WalksPagesUpToTheCap() {
        int pageSize = EntityMapService.PAGE_SIZE;
        List<Long> full = Collections.nCopies(pageSize, 7L);

        EntityMapResponse response = service().build(offset ->
                new PageImpl<>(full, PageRequest.of(offset / pageSize, pageSize), 5000), new FakeSource(Map.of()));

        assertEquals(new EntityMapResponse.Meta(5000, EntityMapService.MAX_FEATURES), response.meta());
        assertEquals(0, response.features().size());
    }

    /**
     * A geometry PostGIS returned unreadable is left off the map, the rest still placed.
     */
    @Test
    void build_LeavesOutAnUnreadableGeometry() {
        FakeSource source = new FakeSource(Map.of(1L, POINT, 2L, "{not json"));

        EntityMapResponse response = service().build(offset ->
                new PageImpl<>(List.of(1L, 2L), PageRequest.of(0, EntityMapService.PAGE_SIZE), 2), source);

        assertEquals(List.of("1"), response.features().stream().map(EntityMapResponse.Feature::id).toList());
    }

    /**
     * A single row with a geometry comes back as its feature, outside of any list.
     */
    @Test
    void feature_WithAGeometry_ReturnsTheRowPlaced() {
        EntityMapFeatureResponse response = service().feature(3L, new FakeSource(Map.of(3L, POINT)));

        assertEquals("3", response.feature().id());
        assertEquals("Point", response.feature().geometry().get("type").asText());
    }

    /**
     * A single row that can't be placed has no feature.
     */
    @Test
    void feature_WithoutGeometry_ReturnsNoFeature() {
        assertNull(service().feature(4L, new FakeSource(Map.of())).feature());
    }
}
