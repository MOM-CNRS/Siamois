package fr.siamois.ui.form.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnWidthTest {

    @Test
    void toPrimeFacesClassName_includesEveryTierThatWasSet() {
        ColumnWidth width = new ColumnWidth(12, 6, 3);

        assertThat(width.toPrimeFacesClassName()).isEqualTo("ui-g-12 ui-md-6 ui-lg-3");
    }

    @Test
    void toPrimeFacesClassName_omitsATierLeftNull() {
        ColumnWidth width = new ColumnWidth(12, null, null);

        assertThat(width.toPrimeFacesClassName()).isEqualTo("ui-g-12");
    }

    @Test
    void toPrimeFacesClassName_canOverrideOnlyOneTier() {
        ColumnWidth width = new ColumnWidth(12, null, 4);

        assertThat(width.toPrimeFacesClassName()).isEqualTo("ui-g-12 ui-lg-4");
    }

    @Test
    void presets_matchTheClassStringsTheyReplace() {
        assertThat(ColumnWidth.STANDARD.toPrimeFacesClassName()).isEqualTo("ui-g-12 ui-md-6 ui-lg-3");
        assertThat(ColumnWidth.HALF.toPrimeFacesClassName()).isEqualTo("ui-g-12 ui-md-6 ui-lg-6");
        assertThat(ColumnWidth.FULL.toPrimeFacesClassName()).isEqualTo("ui-g-12 ui-md-12 ui-lg-12");
    }
}
