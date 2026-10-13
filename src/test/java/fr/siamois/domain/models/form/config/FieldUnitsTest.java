package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.form.config.FieldUnits.UnitRef;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FieldUnitsTest {

    private static final UnitRef SQUARE_METRE = new UnitRef(1L, "m²");
    private static final UnitRef HECTARE = new UnitRef(2L, "ha");

    @Test
    void theDefaultUnitIsOneOfTheUnitsOffered() {
        FieldUnits units = new FieldUnits(2L, List.of(SQUARE_METRE, HECTARE));

        assertThat(units.defaultUnitId()).isEqualTo(2L);
        assertThat(units.defaultSymbol()).isEqualTo("ha");
        assertThat(units.ids()).containsExactly(1L, 2L);
        assertThat(units.symbols()).containsExactly("m²", "ha");
    }

    @Test
    void aDefaultOutsideTheListFallsBackOnTheFirstUnit() {
        assertThat(new FieldUnits(99L, List.of(SQUARE_METRE, HECTARE)).defaultSymbol()).isEqualTo("m²");
        assertThat(new FieldUnits(null, List.of(HECTARE)).defaultUnitId()).isEqualTo(2L);
    }

    @Test
    void aUnitListedTwiceIsKeptOnce_andUnitsWithoutIdAreDropped() {
        FieldUnits units = new FieldUnits(1L, Arrays.asList(SQUARE_METRE, SQUARE_METRE, new UnitRef(null, "?"), null));

        assertThat(units.ids()).containsExactly(1L);
    }

    @Test
    void noUnitsIsEmptyAndHasNoJson() {
        FieldUnits none = new FieldUnits(null, null);

        assertThat(none.isEmpty()).isTrue();
        assertThat(none.defaultSymbol()).isNull();
        assertThat(none.toJson()).isNull();
    }

    @Test
    void theUnitsSurviveAJsonRoundTrip() {
        FieldUnits units = new FieldUnits(2L, List.of(SQUARE_METRE, HECTARE));

        FieldUnits read = FieldUnits.fromJson(units.toJson());

        assertThat(read).isEqualTo(units);
    }

    @Test
    void unreadableOrEmptyJsonIsNoUnits() {
        assertThat(FieldUnits.fromJson(null)).isNull();
        assertThat(FieldUnits.fromJson("  ")).isNull();
        assertThat(FieldUnits.fromJson("not json")).isNull();
        assertThat(FieldUnits.fromJson("{\"defaultUnitId\":1,\"units\":[]}")).isNull();
    }
}
