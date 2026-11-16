package fr.siamois.ui.api.openapi.v1.service.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.map.EntityMapResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingUnitMapFeatureSourceTest {

    @Mock private RecordingUnitRepository repository;
    @Mock private RecordingUnitResponseMapper mapper;
    @InjectMocks private RecordingUnitMapFeatureSource source;

    /**
     * A unit reads as a feature with the identifier, type and status of its list row.
     */
    @Test
    void toFeature_TakesTheListRowsIdentifierTypeAndStatus() throws Exception {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(3L);
        ResolvedConceptResource type = new ResolvedConceptResource();
        type.setResolvedLabel("Couche");
        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setId("3");
        resource.setFullIdentifier("UE-3");
        resource.setType(type);
        resource.setValidationStatus(ValidationStatus.VALIDATED);
        when(mapper.convert(dto)).thenReturn(resource);
        JsonNode geometry = new ObjectMapper().readTree("{\"type\":\"Point\",\"coordinates\":[2.35,48.85]}");

        EntityMapResponse.Feature feature = source.toFeature(dto, geometry);

        assertEquals(new EntityMapResponse.Feature("3", "UE-3", type, ValidationStatus.VALIDATED, geometry), feature);
        assertEquals(3L, source.idOf(dto));
    }

    /**
     * The geometries are PostGIS's, as the repository returns them.
     */
    @Test
    void geoJsonWgs84ByIds_ReadsTheRepository() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{3L, "{}"});
        when(repository.findGeoJsonWgs84ByIds(List.of(3L))).thenReturn(rows);

        assertSame(rows, source.geoJsonWgs84ByIds(List.of(3L)));
    }
}
