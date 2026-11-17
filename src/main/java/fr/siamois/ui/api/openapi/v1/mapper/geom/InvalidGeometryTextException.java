package fr.siamois.ui.api.openapi.v1.mapper.geom;

/**
 * A geometry's text (WKT or EWKT) that cannot be read, or names a geometry type the API does not handle.
 */
public class InvalidGeometryTextException extends IllegalArgumentException {

    /**
     * @param message what is wrong with the text
     * @param cause   the parser's own failure, if any
     */
    public InvalidGeometryTextException(String message, Throwable cause) {
        super(message, cause);
    }
}
