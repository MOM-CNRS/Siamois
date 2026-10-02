package fr.siamois.ui.api.openapi.v1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMapResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingUnitMapServiceTest {

    @Mock private RecordingUnitRepository repository;
    @Mock private RecordingUnitResponseMapper mapper;

    private RecordingUnitMapService service() {
        return new RecordingUnitMapService(repository, mapper, new ObjectMapper());
    }

    private RecordingUnitResource resource(String id) {
        RecordingUnitResource r = new RecordingUnitResource();
        r.setId(id);
        r.setFullIdentifier("UE-" + id);
        return r;
    }

    @Test
    void placesUnitsWithAGeometryAndCountsTheOthers() {
        RecordingUnitDTO a = new RecordingUnitDTO();
        a.setId(1L);
        RecordingUnitDTO b = new RecordingUnitDTO();
        b.setId(2L);
        when(mapper.convert(a)).thenReturn(resource("1"));
        when(mapper.convert(b)).thenReturn(resource("2"));
        // Only unit 1 comes back from PostGIS: unit 2 has no usable geometry.
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, "{\"type\":\"Point\",\"coordinates\":[2.35,48.85]}"});
        when(repository.findGeoJsonWgs84ByIds(any())).thenReturn(rows);

        RecordingUnitMapResponse response = service().build(offset ->
                new PageImpl<>(List.of(a, b), PageRequest.of(0, RecordingUnitMapService.PAGE_SIZE), 2));

        assertEquals(1, response.features().size());
        assertEquals("1", response.features().get(0).id());
        assertEquals("Point", response.features().get(0).geometry().get("type").asText());
        assertEquals(2, response.meta().total());
        assertEquals(1, response.meta().shown());
        assertEquals(1, response.meta().withoutGeometry());
        assertFalse(response.meta().truncated());
    }

    @Test
    void walksPagesUpToTheCapAndSaysWhenItTruncated() {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        when(mapper.convert(any(RecordingUnitDTO.class))).thenAnswer(i -> resource("7"));
        when(repository.findGeoJsonWgs84ByIds(any())).thenReturn(List.of());
        int pageSize = RecordingUnitMapService.PAGE_SIZE;
        List<RecordingUnitDTO> full = Collections.nCopies(pageSize, dto);

        RecordingUnitMapResponse response = service().build(offset ->
                new PageImpl<>(full, PageRequest.of(offset / pageSize, pageSize), 5000));

        assertEquals(RecordingUnitMapService.MAX_FEATURES, response.meta().shown() + response.meta().withoutGeometry());
        assertTrue(response.meta().truncated());
        assertEquals(5000, response.meta().total());
    }
}
