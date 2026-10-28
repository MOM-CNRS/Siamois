package fr.siamois.ui.api.openapi.v1.mapper.geom;

import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.PointDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
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
    void theHelperIsNeverInstantiated() throws NoSuchMethodException {
        Constructor<GeometryText> constructor = GeometryText.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
