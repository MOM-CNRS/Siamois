package fr.siamois.ui.api.openapi.v1.mapper.geom;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.MultiPolygonDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PointDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PolygonDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometryInputTest {

    @Test
    void aValidGeometryIsHandedBackAsIs() {
        PolygonDTO polygon = polygon(new double[][][]{{{0, 0}, {10, 0}, {10, 10}, {0, 0}}});

        assertThat(GeometryInput.requireValid(polygon)).isSameAs(polygon);
        assertThat(GeometryInput.requireValid(null)).isNull();
    }

    @Test
    void aRingThatDoesNotCloseIsA400InFrench() {
        PolygonDTO polygon = polygon(new double[][][]{{{0, 0}, {10, 0}, {10, 10}, {0, 5}}});

        assertThatThrownBy(() -> GeometryInput.requireValid(polygon))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("doit se refermer");
    }

    @Test
    void missingOrIncompleteCoordinatesAreA400() {
        PointDTO noCoordinates = new PointDTO();
        PointDTO oneOrdinate = new PointDTO();
        oneOrdinate.coordinates = new double[]{1};

        assertThatThrownBy(() -> GeometryInput.requireValid(noCoordinates))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("coordonnées manquantes");
        assertThatThrownBy(() -> GeometryInput.requireValid(oneOrdinate))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("coordonnées manquantes");
    }

    @Test
    void aPlaceTakesOnlyAValidMultiPolygon() {
        MultiPolygonDTO multiPolygon = new MultiPolygonDTO();
        multiPolygon.coordinates = new double[][][][]{{{{0, 0}, {10, 0}, {10, 10}, {0, 0}}}};
        PolygonDTO polygon = polygon(new double[][][]{{{0, 0}, {10, 0}, {10, 10}, {0, 0}}});

        assertThat(GeometryInput.requireMultiPolygon(multiPolygon)).isSameAs(multiPolygon);
        assertThat(GeometryInput.requireMultiPolygon(null)).isNull();
        assertThatThrownBy(() -> GeometryInput.requireMultiPolygon(polygon))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MultiPolygon est attendue");
    }

    @Test
    void theHelperIsNeverInstantiated() throws NoSuchMethodException {
        Constructor<GeometryInput> constructor = GeometryInput.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    private static PolygonDTO polygon(double[][][] rings) {
        PolygonDTO polygon = new PolygonDTO();
        polygon.coordinates = rings;
        return polygon;
    }
}
