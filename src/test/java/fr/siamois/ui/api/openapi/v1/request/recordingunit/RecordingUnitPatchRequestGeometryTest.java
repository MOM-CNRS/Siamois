package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.MultiPolygonDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PointDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PolygonDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The geometry a fiche draws on a map (frontend createEntityApi.patchGeometry): a GeoJSON polygon,
 * multipolygon or point in WGS84.
 */
class RecordingUnitPatchRequestGeometryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_drawnPolygon_isAPolygonInWgs84() throws Exception {
        String json = """
                { "geom": { "type": "Polygon", "srid": 4326,
                            "coordinates": [[[2.3, 48.8], [2.4, 48.8], [2.4, 48.9], [2.3, 48.8]]] } }
                """;

        RecordingUnitPatchRequest request = mapper.readValue(json, RecordingUnitPatchRequest.class);

        assertThat(request.isGeomPresent()).isTrue();
        assertThat(request.getGeom()).isInstanceOf(PolygonDTO.class);
        assertThat(request.getGeom().srid).isEqualTo(4326);
        assertThat(((PolygonDTO) request.getGeom()).coordinates[0]).hasNumberOfRows(4);
    }

    @Test
    void deserialize_severalPolygons_isAMultiPolygon() throws Exception {
        String json = """
                { "geom": { "type": "MultiPolygon", "srid": 4326, "coordinates": [
                    [[[0, 0], [1, 0], [1, 1], [0, 0]]],
                    [[[5, 5], [6, 5], [6, 6], [5, 5]]] ] } }
                """;

        RecordingUnitPatchRequest request = mapper.readValue(json, RecordingUnitPatchRequest.class);

        assertThat(request.getGeom()).isInstanceOf(MultiPolygonDTO.class);
        assertThat(((MultiPolygonDTO) request.getGeom()).coordinates).hasNumberOfRows(2);
    }

    @Test
    void deserialize_point_isAPoint() throws Exception {
        String json = """
                { "geom": { "type": "Point", "srid": 4326, "coordinates": [2.35, 48.85] } }
                """;

        RecordingUnitPatchRequest request = mapper.readValue(json, RecordingUnitPatchRequest.class);

        assertThat(request.getGeom()).isInstanceOf(PointDTO.class);
        assertThat(((PointDTO) request.getGeom()).coordinates).containsExactly(2.35, 48.85);
    }

    @Test
    void deserialize_nullGeometry_removesIt() throws Exception {
        RecordingUnitPatchRequest request = mapper.readValue("{ \"geom\": null }", RecordingUnitPatchRequest.class);

        assertThat(request.isGeomPresent()).isTrue();
        assertThat(request.getGeom()).isNull();
    }

    @Test
    void deserialize_withoutGeometry_leavesItUnchanged() throws Exception {
        RecordingUnitPatchRequest request = mapper.readValue("{ \"answers\": {} }", RecordingUnitPatchRequest.class);

        assertThat(request.isGeomPresent()).isFalse();
    }
}
