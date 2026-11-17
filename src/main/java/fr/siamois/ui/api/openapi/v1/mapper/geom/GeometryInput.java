package fr.siamois.ui.api.openapi.v1.mapper.geom;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.MultiPolygonDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Checks a geometry a request carries (GeoJSON) as soon as it comes in: one JTS cannot build — a ring that
 * does not close, missing coordinates — is a 400 the client shows, instead of failing the save later on.
 */
public final class GeometryInput {

    private static final GeometryDtoMapper MAPPER = new GeometryDtoMapper();

    private GeometryInput() {
        throw new UnsupportedOperationException();
    }

    /**
     * Fails with a 400 unless {@code geometry} is null or a geometry JTS can build.
     *
     * @param geometry the geometry of the request
     * @return the same geometry, to be written as is
     */
    public static GeometryDTO requireValid(GeometryDTO geometry) {
        if (geometry == null) {
            return null;
        }
        try {
            MAPPER.toJts(geometry);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Géométrie invalide : " + reasonOf(e), e);
        }
        return geometry;
    }

    /**
     * Fails with a 400 unless {@code geometry} is null or a valid MultiPolygon: the only shape a place has.
     *
     * @param geometry the geometry of the request
     * @return the same geometry, to be written as is
     */
    public static GeometryDTO requireMultiPolygon(GeometryDTO geometry) {
        if (geometry != null && !(geometry instanceof MultiPolygonDTO)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une géométrie de type MultiPolygon est attendue, reçu : " + geometry.type);
        }
        return requireValid(geometry);
    }

    /**
     * What a user reads of JTS's refusal: its most frequent case in French, any other refusal as JTS words it,
     * and a structure it could not even read (missing coordinates) as such.
     */
    static String reasonOf(RuntimeException e) {
        if (!(e instanceof IllegalArgumentException)) {
            return "coordonnées manquantes ou incomplètes";
        }
        String message = String.valueOf(e.getMessage());
        return message.contains("closed linestring")
                ? "chaque anneau d'un polygone doit se refermer (son dernier point doit répéter le premier)"
                : message;
    }
}
