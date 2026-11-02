package fr.siamois.infrastructure.dataimport;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the geometry of an imported row, written as WKT with an optional {@code SRID=3948;} prefix (EWKT).
 * Without a prefix the SRID is left at 0: like the rest of SIAMOIS, the import does not reproject nor assume a CRS.
 */
final class ImportGeometry {

    private static final Pattern SRID_PREFIX = Pattern.compile("^\\s*SRID\\s*=\\s*(\\d+)\\s*;", Pattern.CASE_INSENSITIVE);

    private ImportGeometry() {
    }

    /**
     * @return the geometry, or {@code null} when the cell is blank
     * @throws IllegalArgumentException when the text is not valid WKT
     */
    static Geometry parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int srid = 0;
        String wkt = text.trim();
        Matcher prefix = SRID_PREFIX.matcher(wkt);
        if (prefix.find()) {
            srid = Integer.parseInt(prefix.group(1));
            wkt = wkt.substring(prefix.end()).trim();
        }
        try {
            Geometry geometry = new WKTReader(new GeometryFactory(new PrecisionModel(), srid)).read(wkt.toUpperCase(Locale.ROOT));
            geometry.setSRID(srid);
            return geometry;
        } catch (ParseException e) {
            throw new IllegalArgumentException("géométrie illisible, attendu du WKT (ex. SRID=3948;MULTIPOLYGON(((x y, …)))) : " + e.getMessage(), e);
        }
    }
}
