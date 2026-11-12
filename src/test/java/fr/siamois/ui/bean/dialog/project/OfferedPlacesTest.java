package fr.siamois.ui.bean.dialog.project;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The converter of the place pickers: a key posted back is a place the server offered, or nothing. */
class OfferedPlacesTest {

    private final OfferedPlaces offeredPlaces = new OfferedPlaces();
    private final PlaceChoice lyon = new PlaceChoice("4", 4L, "Lyon", "69123", "SIAMOIS", null, null);

    @Test
    void getAsObject_shouldGiveBackAPlaceOffered() {
        offeredPlaces.remember(List.of(lyon));

        assertThat(offeredPlaces.getAsObject(null, null, offeredPlaces.getAsString(null, null, lyon))).isEqualTo(lyon);
    }

    @Test
    void getAsObject_shouldGiveNothing_forAnEmptyOrUnknownKey() {
        offeredPlaces.remember(lyon);

        assertThat(offeredPlaces.getAsObject(null, null, "")).isNull();
        assertThat(offeredPlaces.getAsObject(null, null, null)).isNull();
        assertThat(offeredPlaces.getAsObject(null, null, "external:INSEE:00000")).isNull();
    }

    @Test
    void forget_shouldForgetEveryPlaceOffered() {
        offeredPlaces.remember(lyon);

        offeredPlaces.forget();

        assertThat(offeredPlaces.getAsObject(null, null, "4")).isNull();
    }

    @Test
    void getAsString_shouldGiveAnEmptyKey_forNoPlace() {
        assertThat(offeredPlaces.getAsString(null, null, null)).isEmpty();
    }
}
