package fr.siamois.ui.form.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomColUiDtoTest {

    @Test
    void getClassName_computesFromWidthWhenSet() {
        CustomColUiDto col = new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).build();

        assertThat(col.getClassName()).isEqualTo("ui-g-12 ui-md-6 ui-lg-3");
    }

    @Test
    void getClassName_appendsDNoneWhenHidden() {
        CustomColUiDto col = new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).hidden(true).build();

        assertThat(col.getClassName()).isEqualTo("ui-g-12 ui-md-6 ui-lg-3 d-none");
    }

    @Test
    void getClassName_fallsBackToTheRawStringWhenNoWidthWasSet() {
        // Every form that hasn't migrated to .width(...) yet (Container/Phase/Specimen/
        // SpatialUnit/*NewForm) still builds columns with the old .className(String) — JSF's own
        // col.className binding must see the exact same value either way.
        CustomColUiDto col = new CustomColUiDto.Builder().className("ui-g-12 ui-md-6 ui-lg-4").build();

        assertThat(col.getClassName()).isEqualTo("ui-g-12 ui-md-6 ui-lg-4");
    }

    @Test
    void width_isNullByDefault() {
        CustomColUiDto col = new CustomColUiDto.Builder().build();

        assertThat(col.getWidth()).isNull();
        assertThat(col.getClassName()).isNull();
    }
}
