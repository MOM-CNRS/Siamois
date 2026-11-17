package fr.siamois.ui.api.openapi.v1.mapper.geom;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PointDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PolygonDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometryTextTest {

    @Test
    void aGeometryIsShownAsItsWkt() {
        PointDTO point = new PointDTO();
        point.coordinates = new double[]{2.5, 48.5};

        assertThat(GeometryText.of(point)).isEqualTo("POINT (2.5 48.5)");
    }

    @Test
    void noGeometryIsNoText() {
        assertThat(GeometryText.of(null)).isNull();
    }

    @Test
    void aGeometryWithoutTypeIsNoText() {
        assertThat(GeometryText.of(new GeometryDTO() { })).isNull();
    }

    @Test
    void aWktNamingNoSridTakesTheFallbackOne() {
        PointDTO point = (PointDTO) GeometryText.parse("POINT (650000 6860000)", 2154);

        assertThat(point.coordinates).containsExactly(650000, 6860000);
        assertThat(point.srid).isEqualTo(2154);
    }

    @Test
    void anEwktKeepsTheSridItNames() {
        GeometryDTO polygon = GeometryText.parse("  srid=4326;POLYGON ((0 0, 1 0, 1 1, 0 0))\n", 2154);

        assertThat(polygon).isInstanceOf(PolygonDTO.class);
        assertThat(polygon.srid).isEqualTo(4326);
    }

    @Test
    void aWktWithoutSridNorFallbackHasNone() {
        assertThat(GeometryText.parse("POINT (1 2)", null).srid).isNull();
    }

    @Test
    void aBlankTextOrAnEmptyGeometryIsNoGeometry() {
        assertThat(GeometryText.parse(null, 2154)).isNull();
        assertThat(GeometryText.parse("  ", 2154)).isNull();
        assertThat(GeometryText.parse("POINT EMPTY", 2154)).isNull();
    }

    @Test
    void theShownTextReadsBackAsTheSameGeometry() {
        String wkt = "MULTIPOLYGON (((1587452.963 7250980.855, 1587454.281 7250981.951, 1587453.5 7250982.1, 1587452.963 7250980.855)))";

        assertThat(GeometryText.of(GeometryText.parse(wkt, 2154))).isEqualTo(wkt);
    }

    @Test
    void aTextThatIsNoGeometryIsRefused() {
        assertThatThrownBy(() -> GeometryText.parse("POINT (1", null)).isInstanceOf(InvalidGeometryTextException.class);
        assertThatThrownBy(() -> GeometryText.parse("SRID=abc;POINT (1 2)", null)).isInstanceOf(InvalidGeometryTextException.class);
        assertThatThrownBy(() -> GeometryText.requireReadable("rien")).isInstanceOf(InvalidGeometryTextException.class);
    }

    @Test
    void aGeometryOfATypeTheApiDoesNotCarryIsRefused_butReadable() {
        String lines = "MULTILINESTRING ((0 0, 1 1), (2 2, 3 3))";

        assertThatThrownBy(() -> GeometryText.parse(lines, null))
                .isInstanceOf(InvalidGeometryTextException.class)
                .hasMessageContaining("MultiLineString");
        assertThatCode(() -> GeometryText.requireReadable(lines)).doesNotThrowAnyException();
        assertThatCode(() -> GeometryText.requireReadable("")).doesNotThrowAnyException();
    }

    @Test
    void theHelperIsNeverInstantiated() throws NoSuchMethodException {
        Constructor<GeometryText> constructor = GeometryText.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
