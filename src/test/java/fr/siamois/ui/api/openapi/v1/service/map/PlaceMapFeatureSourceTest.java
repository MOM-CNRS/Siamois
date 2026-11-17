package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.ui.api.openapi.v1.mapper.PlaceOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceMapFeatureSourceTest {

    @Mock private SpatialUnitRepository repository;
    @Mock private PlaceOpenApiMapper mapper;
    @InjectMocks private PlaceMapFeatureSource source;

    /**
     * A place reads as a feature with the name, category and status of its list row.
     */
    @Test
    void toFeature_TakesTheListRowsNameCategoryAndStatus() throws Exception {
        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setId(7L);
        ResolvedConceptResource type = new ResolvedConceptResource();
        type.setResolvedLabel("Commune");
        PlaceResource resource = new PlaceResource();
        resource.setId("7");
        resource.setName("Lyon");
        resource.setType(type);
        resource.setValidationStatus(ValidationStatus.VALIDATED);
        when(mapper.toResource(dto, null)).thenReturn(resource);
        JsonNode geometry = new ObjectMapper().readTree("{\"type\":\"Point\",\"coordinates\":[4.83,45.76]}");

        EntityMapResponse.Feature feature = source.toFeature(dto, geometry);

        assertEquals(new EntityMapResponse.Feature("7", "Lyon", type, ValidationStatus.VALIDATED, geometry), feature);
        assertEquals(7L, source.idOf(dto));
    }

    /**
     * The place form is fixed: its own geometry is the one geometry column.
     */
    @Test
    void geometryColumns_AreOnlyTheOwnGeometry() {
        assertEquals(-210L, source.defaultGeometryFieldId());
        assertTrue(source.isGeometryColumn(-210L));
        assertFalse(source.isGeometryColumn(-332L));
    }

    /**
     * The geometries are PostGIS's, reprojected.
     */
    @Test
    void geoJsonWgs84ByIds_ReadsTheOwnGeometries() {
        List<Object[]> own = new ArrayList<>();
        own.add(new Object[]{7L, "{}"});
        when(repository.findGeoJsonWgs84ByIds(List.of(7L))).thenReturn(own);

        assertSame(own, source.geoJsonWgs84ByIds(List.of(7L), -210L));
    }
}
