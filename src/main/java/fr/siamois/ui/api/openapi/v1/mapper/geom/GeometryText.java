package fr.siamois.ui.api.openapi.v1.mapper.geom;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text a form shows and edits for a geometry: its WKT, in the SRID it carries (never reprojected).
 * What the form writes back is WKT too, or EWKT ({@code SRID=2154;POINT (…)}) to name its SRID.
 */
public final class GeometryText {

    private static final GeometryDtoMapper MAPPER = new GeometryDtoMapper();

    /** The {@code SRID=…;} prefix of an EWKT, then the WKT itself. */
    private static final Pattern EWKT = Pattern.compile("^SRID=(\\d+);(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private GeometryText() {
        throw new UnsupportedOperationException();
    }

    /** The WKT of {@code dto}, or null when there is no geometry. */
    public static String of(GeometryDTO dto) {
        Geometry geometry = MAPPER.toJts(dto);
        return geometry == null ? null : geometry.toText();
    }

    /**
     * The geometry {@code text} stands for, as the API carries it.
     *
     * @param text         a WKT, or an EWKT naming its SRID
     * @param fallbackSrid the SRID of a WKT that names none (the geometry's current one, so editing its
     *                     coordinates keeps it), null for none
     * @return null when the text is blank or an empty geometry: the geometry is cleared
     * @throws InvalidGeometryTextException when the text is not a geometry, or one of a type the API
     *                                      does not handle
     */
    public static GeometryDTO parse(String text, Integer fallbackSrid) {
        Geometry geometry = read(text);
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry.getSRID() == 0 && fallbackSrid != null) {
            geometry.setSRID(fallbackSrid);
        }
        try {
            return MAPPER.toDto(geometry);
        } catch (IllegalArgumentException e) {
            throw new InvalidGeometryTextException("Type de géométrie non pris en charge : " + geometry.getGeometryType(), e);
        }
    }

    /**
     * Fails unless {@code text} is blank or a geometry: what an additional geometry field, kept as its
     * text, accepts.
     *
     * @throws InvalidGeometryTextException when the text is not a geometry
     */
    public static void requireReadable(String text) {
        read(text);
    }

    private static Geometry read(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String wkt = text.strip();
        int srid = 0;
        Matcher ewkt = EWKT.matcher(wkt);
        try {
            if (ewkt.matches()) {
                srid = Integer.parseInt(ewkt.group(1));
                wkt = ewkt.group(2).strip();
            }
            Geometry geometry = new WKTReader().read(wkt);
            geometry.setSRID(srid);
            return geometry;
        } catch (ParseException | NumberFormatException e) {
            throw new InvalidGeometryTextException("Géométrie illisible (WKT ou EWKT attendu) : " + e.getMessage(), e);
        }
    }
}
