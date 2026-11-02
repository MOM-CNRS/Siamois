package fr.siamois.infrastructure.dataimport;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportGeometryTest {

    @Test
    void parse_blankCell_returnsNull() {
        assertThat(ImportGeometry.parse(null)).isNull();
        assertThat(ImportGeometry.parse("  ")).isNull();
    }

    @Test
    void parse_sridPrefix_isKept() {
        Geometry g = ImportGeometry.parse("SRID=3948;MultiPolygon (((0 0, 1 0, 1 1, 0 0)))");

        assertThat(g.getGeometryType()).isEqualTo("MultiPolygon");
        assertThat(g.getSRID()).isEqualTo(3948);
    }

    @Test
    void parse_withoutPrefix_leavesSridAtZero() {
        assertThat(ImportGeometry.parse("POINT (1 2)").getSRID()).isZero();
    }

    @Test
    void parse_invalidText_isRejected() {
        assertThatThrownBy(() -> ImportGeometry.parse("pas du wkt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WKT");
    }
}
