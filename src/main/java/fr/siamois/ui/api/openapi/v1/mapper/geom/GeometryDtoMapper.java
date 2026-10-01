package fr.siamois.ui.api.openapi.v1.mapper.geom;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.LineStringDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.MultiPointDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.MultiPolygonDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PointDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PolygonDTO;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Converts between JTS geometries and the GeoJSON-style {@link GeometryDTO} hierarchy.
 * No CRS is imposed: the SRID carried by the DTO (or the JTS geometry) is used as-is,
 * never reprojected.
 */
@Component
public class GeometryDtoMapper {

    public GeometryDTO toDto(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        Integer srid = geometry.getSRID() == 0 ? null : geometry.getSRID();
        if (geometry instanceof MultiPolygon multiPolygon) {
            MultiPolygonDTO dto = new MultiPolygonDTO();
            dto.coordinates = new double[multiPolygon.getNumGeometries()][][][];
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                dto.coordinates[i] = polygonCoordinates((Polygon) multiPolygon.getGeometryN(i));
            }
            dto.srid = srid;
            return dto;
        }
        if (geometry instanceof Polygon polygon) {
            PolygonDTO dto = new PolygonDTO();
            dto.coordinates = polygonCoordinates(polygon);
            dto.srid = srid;
            return dto;
        }
        if (geometry instanceof MultiPoint multiPoint) {
            MultiPointDTO dto = new MultiPointDTO();
            dto.coordinates = ringCoordinates(multiPoint.getCoordinates());
            dto.srid = srid;
            return dto;
        }
        if (geometry instanceof LineString lineString) {
            LineStringDTO dto = new LineStringDTO();
            dto.coordinates = ringCoordinates(lineString.getCoordinates());
            dto.srid = srid;
            return dto;
        }
        if (geometry instanceof Point point) {
            PointDTO dto = new PointDTO();
            dto.coordinates = coordinate(point.getCoordinate());
            dto.srid = srid;
            return dto;
        }
        throw new IllegalArgumentException("Unsupported geometry type: " + geometry.getGeometryType());
    }

    public Geometry toJts(GeometryDTO dto) {
        if (dto == null || dto.type == null) {
            return null;
        }
        int srid = dto.srid != null ? dto.srid : 0;
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), srid);
        return switch (dto.type) {
            case "Point" -> {
                PointDTO point = (PointDTO) dto;
                yield factory.createPoint(toCoordinate(point.coordinates));
            }
            case "MultiPoint" -> {
                MultiPointDTO multiPoint = (MultiPointDTO) dto;
                yield factory.createMultiPointFromCoords(toCoordinates(multiPoint.coordinates));
            }
            case "LineString" -> {
                LineStringDTO lineString = (LineStringDTO) dto;
                yield factory.createLineString(toCoordinates(lineString.coordinates));
            }
            case "Polygon" -> {
                PolygonDTO polygon = (PolygonDTO) dto;
                yield createPolygon(factory, polygon.coordinates);
            }
            case "MultiPolygon" -> {
                MultiPolygonDTO multiPolygon = (MultiPolygonDTO) dto;
                Polygon[] polygons = new Polygon[multiPolygon.coordinates.length];
                for (int i = 0; i < multiPolygon.coordinates.length; i++) {
                    polygons[i] = createPolygon(factory, multiPolygon.coordinates[i]);
                }
                yield factory.createMultiPolygon(polygons);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Type de géométrie non supporté : " + dto.type);
        };
    }

    public MultiPolygon toMultiPolygon(GeometryDTO dto) {
        if (dto == null) {
            return null;
        }
        Geometry geometry = toJts(dto);
        if (!(geometry instanceof MultiPolygon multiPolygon)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une géométrie de type MultiPolygon est attendue, reçu : " + dto.type);
        }
        return multiPolygon;
    }

    private static double[][][] polygonCoordinates(Polygon polygon) {
        double[][][] rings = new double[polygon.getNumInteriorRing() + 1][][];
        rings[0] = ringCoordinates(polygon.getExteriorRing().getCoordinates());
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            rings[i + 1] = ringCoordinates(polygon.getInteriorRingN(i).getCoordinates());
        }
        return rings;
    }

    private static double[][] ringCoordinates(Coordinate[] coordinates) {
        double[][] ring = new double[coordinates.length][];
        for (int i = 0; i < coordinates.length; i++) {
            ring[i] = coordinate(coordinates[i]);
        }
        return ring;
    }

    private static double[] coordinate(Coordinate c) {
        return new double[]{c.getX(), c.getY()};
    }

    private static Coordinate toCoordinate(double[] c) {
        return new Coordinate(c[0], c[1]);
    }

    private static Coordinate[] toCoordinates(double[][] coordinates) {
        Coordinate[] result = new Coordinate[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            result[i] = toCoordinate(coordinates[i]);
        }
        return result;
    }

    private static Polygon createPolygon(GeometryFactory factory, double[][][] rings) {
        LinearRing shell = factory.createLinearRing(toCoordinates(rings[0]));
        LinearRing[] holes = new LinearRing[rings.length - 1];
        for (int i = 1; i < rings.length; i++) {
            holes[i - 1] = factory.createLinearRing(toCoordinates(rings[i]));
        }
        return factory.createPolygon(shell, holes);
    }
}
