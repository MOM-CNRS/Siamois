package fr.siamois.ui.api.openapi.v1.mapper.geom;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;

/**
 * The text a form shows for a geometry: its WKT, in the SRID it carries (never reprojected). The geometry is
 * not edited through the form, only displayed.
 */
public final class GeometryText {

    private static final GeometryDtoMapper MAPPER = new GeometryDtoMapper();

    private GeometryText() {
        throw new UnsupportedOperationException();
    }

    /** The WKT of {@code dto}, or null when there is no geometry. */
    public static String of(GeometryDTO dto) {
        return dto == null ? null : MAPPER.toJts(dto).toText();
    }
}
