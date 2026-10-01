package fr.siamois.ui.api.openapi.v1.mapper.geom;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.LineStringDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.MultiPointDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.MultiPolygonDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PointDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PolygonDTO;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class GeometryDtoMapperTest {

    private final GeometryDtoMapper mapper = new GeometryDtoMapper();
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 3948);

    // ---------- toDto ----------

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void toDto_point_mapsCoordinatesAndSrid() {
        Point point = factory.createPoint(new org.locationtech.jts.geom.Coordinate(1.5, 2.5));

        GeometryDTO dto = mapper.toDto(point);

        assertThat(dto).isInstanceOf(PointDTO.class);
        assertThat(dto.type).isEqualTo("Point");
        assertThat(dto.srid).isEqualTo(3948);
        PointDTO pointDto = (PointDTO) dto;
        assertThat(pointDto.coordinates).containsExactly(1.5, 2.5);
    }

    @Test
    void toDto_zeroSrid_mapsToNullSrid() {
        GeometryFactory noSridFactory = new GeometryFactory();
        Point point = noSridFactory.createPoint(new org.locationtech.jts.geom.Coordinate(1, 2));

        GeometryDTO dto = mapper.toDto(point);

        assertThat(dto.srid).isNull();
    }

    @Test
    void toDto_lineString_mapsCoordinates() {
        LineString lineString = factory.createLineString(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(0, 0),
                new org.locationtech.jts.geom.Coordinate(1, 1)
        });

        GeometryDTO dto = mapper.toDto(lineString);

        assertThat(dto).isInstanceOf(LineStringDTO.class);
        LineStringDTO lineDto = (LineStringDTO) dto;
        assertThat(lineDto.coordinates).isDeepEqualTo(new double[][]{{0, 0}, {1, 1}});
    }

    @Test
    void toDto_multiPoint_mapsCoordinates() {
        MultiPoint multiPoint = factory.createMultiPointFromCoords(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(0, 0),
                new org.locationtech.jts.geom.Coordinate(2, 3)
        });

        GeometryDTO dto = mapper.toDto(multiPoint);

        assertThat(dto).isInstanceOf(MultiPointDTO.class);
        MultiPointDTO multiPointDto = (MultiPointDTO) dto;
        assertThat(multiPointDto.coordinates).isDeepEqualTo(new double[][]{{0, 0}, {2, 3}});
    }

    @Test
    void toDto_polygonWithHole_mapsAllRings() {
        Polygon polygon = squareWithHole();

        GeometryDTO dto = mapper.toDto(polygon);

        assertThat(dto).isInstanceOf(PolygonDTO.class);
        PolygonDTO polygonDto = (PolygonDTO) dto;
        assertThat(polygonDto.coordinates.length).isEqualTo(2); // exterior ring + 1 hole
        assertThat(polygonDto.coordinates[0].length).isEqualTo(5); // closed square ring
        assertThat(polygonDto.coordinates[1].length).isEqualTo(5); // closed hole ring
    }

    @Test
    void toDto_multiPolygon_mapsEachPolygon() {
        MultiPolygon multiPolygon = factory.createMultiPolygon(new Polygon[]{simpleSquare(0), simpleSquare(10)});

        GeometryDTO dto = mapper.toDto(multiPolygon);

        assertThat(dto).isInstanceOf(MultiPolygonDTO.class);
        MultiPolygonDTO multiPolygonDto = (MultiPolygonDTO) dto;
        assertThat(multiPolygonDto.coordinates.length).isEqualTo(2);
        assertThat(multiPolygonDto.srid).isEqualTo(3948);
    }

    @Test
    void toDto_unsupportedGeometryType_throws() {
        Geometry collection = factory.createGeometryCollection(new Geometry[]{
                factory.createPoint(new org.locationtech.jts.geom.Coordinate(0, 0))
        });

        assertThatThrownBy(() -> mapper.toDto(collection))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- toJts ----------

    @Test
    void toJts_null_returnsNull() {
        assertThat(mapper.toJts(null)).isNull();
    }

    @Test
    void toJts_missingType_returnsNull() {
        PointDTO dto = new PointDTO();
        dto.type = null;
        dto.coordinates = new double[]{1, 2};

        assertThat(mapper.toJts(dto)).isNull();
    }

    @Test
    void toJts_point_buildsPointWithSrid() {
        PointDTO dto = new PointDTO();
        dto.coordinates = new double[]{4.5, 6.5};
        dto.srid = 4326;

        Geometry geometry = mapper.toJts(dto);

        assertThat(geometry).isInstanceOf(Point.class);
        assertThat(geometry.getSRID()).isEqualTo(4326);
        assertThat(geometry.getCoordinate().x).isEqualTo(4.5, offset(1e-9));
        assertThat(geometry.getCoordinate().y).isEqualTo(6.5, offset(1e-9));
    }

    @Test
    void toJts_pointWithoutSrid_defaultsToZero() {
        PointDTO dto = new PointDTO();
        dto.coordinates = new double[]{1, 1};

        Geometry geometry = mapper.toJts(dto);

        assertThat(geometry.getSRID()).isZero();
    }

    @Test
    void toJts_multiPoint_buildsMultiPoint() {
        MultiPointDTO dto = new MultiPointDTO();
        dto.coordinates = new double[][]{{0, 0}, {1, 1}};
        dto.srid = 2154;

        Geometry geometry = mapper.toJts(dto);

        assertThat(geometry).isInstanceOf(MultiPoint.class);
        assertThat(geometry.getSRID()).isEqualTo(2154);
        assertThat(geometry.getNumGeometries()).isEqualTo(2);
    }

    @Test
    void toJts_lineString_buildsLineString() {
        LineStringDTO dto = new LineStringDTO();
        dto.coordinates = new double[][]{{0, 0}, {5, 5}};

        Geometry geometry = mapper.toJts(dto);

        assertThat(geometry).isInstanceOf(LineString.class);
        assertThat(geometry.getCoordinates()).hasSize(2);
    }

    @Test
    void toJts_polygonWithHole_buildsPolygonWithHole() {
        PolygonDTO dto = new PolygonDTO();
        dto.coordinates = new double[][][]{
                closedSquareRing(0, 10),
                closedSquareRing(2, 4)
        };

        Geometry geometry = mapper.toJts(dto);

        assertThat(geometry).isInstanceOf(Polygon.class);
        Polygon polygon = (Polygon) geometry;
        assertThat(polygon.getNumInteriorRing()).isEqualTo(1);
    }

    @Test
    void toJts_multiPolygon_buildsMultiPolygon() {
        MultiPolygonDTO dto = new MultiPolygonDTO();
        dto.coordinates = new double[][][][]{
                {closedSquareRing(0, 10)},
                {closedSquareRing(20, 30)}
        };
        dto.srid = 3948;

        Geometry geometry = mapper.toJts(dto);

        assertThat(geometry).isInstanceOf(MultiPolygon.class);
        assertThat(geometry.getNumGeometries()).isEqualTo(2);
        assertThat(geometry.getSRID()).isEqualTo(3948);
    }

    @Test
    void toJts_unknownType_throwsBadRequest() {
        GeometryDTO dto = new PointDTO();
        dto.type = "GeometryCollection";

        assertThatThrownBy(() -> mapper.toJts(dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("GeometryCollection");
    }

    // ---------- toMultiPolygon ----------

    @Test
    void toMultiPolygon_null_returnsNull() {
        assertThat(mapper.toMultiPolygon(null)).isNull();
    }

    @Test
    void toMultiPolygon_validMultiPolygonDto_returnsMultiPolygon() {
        MultiPolygonDTO dto = new MultiPolygonDTO();
        dto.coordinates = new double[][][][]{{closedSquareRing(0, 10)}};
        dto.srid = 3948;

        MultiPolygon result = mapper.toMultiPolygon(dto);

        assertThat(result.getSRID()).isEqualTo(3948);
        assertThat(result.getNumGeometries()).isEqualTo(1);
    }

    @Test
    void toMultiPolygon_wrongGeometryType_throwsBadRequest() {
        PointDTO dto = new PointDTO();
        dto.coordinates = new double[]{0, 0};

        assertThatThrownBy(() -> mapper.toMultiPolygon(dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MultiPolygon");
    }

    // ---------- round-trip ----------

    @Test
    void roundTrip_multiPolygon_preservesCoordinatesAndSrid() {
        MultiPolygonDTO original = new MultiPolygonDTO();
        original.coordinates = new double[][][][]{{closedSquareRing(0, 10)}};
        original.srid = 3948;

        GeometryDTO roundTripped = mapper.toDto(mapper.toJts(original));

        assertThat(roundTripped).isInstanceOf(MultiPolygonDTO.class);
        assertThat(roundTripped.srid).isEqualTo(3948);
        assertThat(((MultiPolygonDTO) roundTripped).coordinates).isDeepEqualTo(original.coordinates);
    }

    // ---------- fixtures ----------

    private Polygon simpleSquare(double offset) {
        return factory.createPolygon(factory.createLinearRing(toCoordinates(closedSquareRing(offset, offset + 10))));
    }

    private Polygon squareWithHole() {
        return factory.createPolygon(
                factory.createLinearRing(toCoordinates(closedSquareRing(0, 10))),
                new org.locationtech.jts.geom.LinearRing[]{
                        factory.createLinearRing(toCoordinates(closedSquareRing(2, 4)))
                });
    }

    private static double[][] closedSquareRing(double min, double max) {
        return new double[][]{
                {min, min}, {max, min}, {max, max}, {min, max}, {min, min}
        };
    }

    private static org.locationtech.jts.geom.Coordinate[] toCoordinates(double[][] coords) {
        org.locationtech.jts.geom.Coordinate[] result = new org.locationtech.jts.geom.Coordinate[coords.length];
        for (int i = 0; i < coords.length; i++) {
            result[i] = new org.locationtech.jts.geom.Coordinate(coords[i][0], coords[i][1]);
        }
        return result;
    }
}
