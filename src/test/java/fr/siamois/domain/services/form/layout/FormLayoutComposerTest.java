package fr.siamois.domain.services.form.layout;

import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.form.dto.ColumnWidth;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.utils.TestForms;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormLayoutComposerTest {

    private static final ConfigurableTable TABLE = ConfigurableTable.CONTENANT;

    @Test
    void aGroupBecomesAPanelOfOneRowOfItsActiveFields() {
        var type = SystemFieldCatalog.fieldBoundTo(TABLE, "type");
        var length = SystemFieldCatalog.fieldBoundTo(TABLE, "length");
        var width = SystemFieldCatalog.fieldBoundTo(TABLE, "width");
        FormLayout layout = new FormLayout(List.of(
                new FormLayout.Group(1L, "Identité", List.of(
                        item(type, FieldWidth.THREE_QUARTERS, true, true))),
                new FormLayout.Group(2L, "Mesures", List.of(
                        item(length, FieldWidth.HALF, true, false),
                        item(width, FieldWidth.HALF, false, false)))));

        FormUiDto form = FormLayoutComposer.compose(layout, TABLE);

        assertThat(form.getLayout()).extracting(p -> p.getName()).containsExactly("Identité", "Mesures");
        var measures = form.getLayout().get(1).getRows();
        assertThat(measures).hasSize(1);
        assertThat(measures.get(0).getColumns()).extracting(c -> c.getField().getValueBinding()).containsExactly("length");
        assertThat(measures.get(0).getColumns().get(0).getWidth()).isEqualTo(ColumnWidth.HALF);
    }

    @Test
    void threeQuartersIsTheWholeRowFromTheMediumBreakpoint() {
        assertThat(FieldWidth.THREE_QUARTERS.toColumnWidth()).isEqualTo(new ColumnWidth(12, 12, 9));
        assertThat(FieldWidth.QUARTER.toColumnWidth()).isEqualTo(ColumnWidth.STANDARD);
        assertThat(FieldWidth.FULL.toColumnWidth()).isEqualTo(ColumnWidth.FULL);
    }

    @Test
    void hiddenSystemFieldsAreCarriedByTheFirstGroupWhateverTheLayoutSays() {
        FormUiDto form = TestForms.of(TABLE);

        List<CustomColUiDto> firstGroup = form.getLayout().get(0).getRows().get(0).getColumns();
        assertThat(firstGroup.stream().filter(CustomColUiDto::isHidden))
                .extracting(c -> c.getField().getValueBinding())
                .containsExactlyInAnyOrder("identifier", "actionUnit");
        assertThat(form.getLayout().get(1).getRows().get(0).getColumns()).noneMatch(CustomColUiDto::isHidden);
    }

    @Test
    void readOnlyComesFromTheFieldsDeclaration() {
        FormUiDto form = TestForms.of(TABLE);

        CustomColUiDto finds = form.getLayout().stream().flatMap(p -> p.getRows().stream())
                .flatMap(r -> r.getColumns().stream())
                .filter(c -> "specimens".equals(c.getField().getValueBinding())).findFirst().orElseThrow();
        assertThat(finds.isReadOnly()).isTrue();
    }

    private FormLayout.Item item(fr.siamois.domain.models.form.customfield.CustomField field, FieldWidth width,
                                 boolean active, boolean mandatory) {
        return new FormLayout.Item(field, width, active, mandatory, false, FieldRules.NONE);
    }
}
