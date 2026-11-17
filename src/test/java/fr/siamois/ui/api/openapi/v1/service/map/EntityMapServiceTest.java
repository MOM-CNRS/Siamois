package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.dto.FieldQuery;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapFeatureResponse;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMapServiceTest {

    private static final String POINT = "{\"type\":\"Point\",\"coordinates\":[2.35,48.85]}";
    private static final String LINE = "{\"type\":\"LineString\",\"coordinates\":[[2,48],[3,49]]}";
    private static final long OWN = -1L;
    private static final long ADDITIONAL = 40L;

    /**
     * A collection whose rows are their own ids, placed by geometry column (field id → GeoJSON by row);
     * its geometry filter records, when applied, the column it was made for.
     */
    private record FakeSource(Map<Long, Map<Long, String>> byColumn, List<Long> filteredBy) implements MapFeatureSource<Long> {

        /**
         * A collection placed by geometry column.
         */
        FakeSource(Map<Long, Map<Long, String>> byColumn) {
            this(byColumn, new ArrayList<>());
        }

        /**
         * A collection placed by its own geometries only.
         */
        static FakeSource ofOwn(Map<Long, String> geoJsonById) {
            return new FakeSource(Map.of(OWN, geoJsonById));
        }

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
        public long defaultGeometryFieldId() {
            return OWN;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isGeometryColumn(long fieldId) {
            return fieldId == OWN || fieldId == ADDITIONAL;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Specification<?> withGeometry(long geometryFieldId) {
            return (root, query, cb) -> {
                filteredBy.add(geometryFieldId);
                return null;
            };
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<Object[]> geoJsonWgs84ByIds(Collection<Long> ids, long geometryFieldId) {
            Map<Long, String> geoJsonById = byColumn.getOrDefault(geometryFieldId, Map.of());
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
     * A page holding the given rows, and that many in all.
     */
    private static PageImpl<Long> pageOf(List<Long> rows) {
        return new PageImpl<>(rows, PageRequest.of(0, EntityMapService.PAGE_SIZE), rows.size());
    }

    /**
     * A row with a geometry is placed, one without is only examined.
     */
    @Test
    void build_PlacesRowsWithAGeometryAndExaminesTheOthers() {
        EntityMapResponse response = service().build(FieldQuery.NONE, (offset, query) -> pageOf(List.of(1L, 2L)), FakeSource.ofOwn(Map.of(1L, POINT)), null);

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

        EntityMapResponse response = service().build(FieldQuery.NONE, (offset, query) ->
                new PageImpl<>(full, PageRequest.of(offset / pageSize, pageSize), 5000), FakeSource.ofOwn(Map.of()), null);

        assertEquals(new EntityMapResponse.Meta(5000, EntityMapService.MAX_FEATURES), response.meta());
        assertEquals(0, response.features().size());
    }

    /**
     * A geometry PostGIS returned unreadable is left off the map, the rest still placed.
     */
    @Test
    void build_LeavesOutAnUnreadableGeometry() {
        EntityMapResponse response = service().build(FieldQuery.NONE, (offset, query) -> pageOf(List.of(1L, 2L)),
                FakeSource.ofOwn(Map.of(1L, POINT, 2L, "{not json")), null);

        assertEquals(List.of("1"), response.features().stream().map(EntityMapResponse.Feature::id).toList());
    }

    /**
     * By an additional geometry field, the rows are placed by its answers, not their own geometry.
     */
    @Test
    void build_ByAnAdditionalGeometryField_PlacesTheRowsByItsAnswers() {
        FakeSource source = new FakeSource(Map.of(OWN, Map.of(1L, POINT), ADDITIONAL, Map.of(2L, LINE)));

        EntityMapResponse response = service().build(FieldQuery.NONE, (offset, query) -> pageOf(List.of(1L, 2L)), source, ADDITIONAL);

        assertEquals(List.of("2"), response.features().stream().map(EntityMapResponse.Feature::id).toList());
        assertEquals("LineString", response.features().get(0).geometry().get("type").asText());
    }

    /**
     * The list is queried narrowed to the rows whose geometry column, the one asked for, is not empty.
     */
    @Test
    void build_QueriesTheListNarrowedToTheRowsWithAGeometry() {
        FakeSource source = FakeSource.ofOwn(Map.of());
        List<FieldQuery> queried = new ArrayList<>();

        service().build(FieldQuery.NONE, (offset, query) -> {
            queried.add(query);
            return pageOf(List.of(1L));
        }, source, ADDITIONAL);

        assertTrue(queried.get(0).filtered());
        queried.get(0).specificationFor().toPredicate(null, null, null);
        assertEquals(List.of(ADDITIONAL), source.filteredBy());
    }

    /**
     * A field that is no geometry column of the collection is refused.
     */
    @Test
    void build_UnknownGeometryColumn_IsABadRequest() {
        FakeSource source = FakeSource.ofOwn(Map.of());
        var service = service();
        assertThrows(ResponseStatusException.class, () -> service.build(FieldQuery.NONE, (offset, query) -> pageOf(List.of(1L)), source, -99L));
    }

    /**
     * A single row with a geometry comes back as its feature, outside of any list, by the column asked for.
     */
    @Test
    void feature_WithAGeometry_ReturnsTheRowPlaced() {
        FakeSource source = new FakeSource(Map.of(OWN, Map.of(3L, POINT), ADDITIONAL, Map.of(3L, LINE)));

        EntityMapFeatureResponse own = service().feature(3L, source, null);
        EntityMapFeatureResponse additional = service().feature(3L, source, ADDITIONAL);

        assertEquals("Point", own.feature().geometry().get("type").asText());
        assertEquals("LineString", additional.feature().geometry().get("type").asText());
    }

    /**
     * A single row that can't be placed has no feature.
     */
    @Test
    void feature_WithoutGeometry_ReturnsNoFeature() {
        assertNull(service().feature(4L, FakeSource.ofOwn(Map.of()), null).feature());
    }
}
