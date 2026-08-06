package fr.siamois.domain.models.form.customform;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CustomFormComposerTest {

    private FormUiDto baseForm() {
        return new FormUiDto.Builder()
                .addPanel(new CustomFormPanelUiDto.Builder()
                        .name("General")
                        .isSystemPanel(true)
                        .addRow(new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder()
                                        .field(CustomFieldText.builder().id(1L).label("name").valueBinding("name").build())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private FormUiDto baseFormWithTwoRows() {
        return new FormUiDto.Builder()
                .addPanel(new CustomFormPanelUiDto.Builder()
                        .name("General")
                        .isSystemPanel(true)
                        .addRow(new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder()
                                        .field(CustomFieldText.builder().id(1L).label("name").valueBinding("name").build())
                                        .build())
                                .addColumn(new CustomColUiDto.Builder()
                                        .field(CustomFieldText.builder().id(2L).label("description").valueBinding("description").build())
                                        .build())
                                .build())
                        .addRow(new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder()
                                        .field(CustomFieldText.builder().id(3L).label("extra").valueBinding("extra").build())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private CustomColUiDto additionalColumn(String label) {
        return new CustomColUiDto.Builder()
                .field(CustomFieldText.builder().id(100L).label(label).valueBinding(label).build())
                .build();
    }

    @Test
    void withAdditionalFields_shouldReturnBaseFormUnchangedWhenNoAdditionalFields() {
        FormUiDto base = baseForm();

        assertThat(CustomFormComposer.withAdditionalFields(base, "Additional", List.of())).isSameAs(base);
        assertThat(CustomFormComposer.withAdditionalFields(base, "Additional", null)).isSameAs(base);
    }

    @Test
    void withAdditionalFields_shouldAppendATrailingPanel() {
        FormUiDto base = baseForm();
        List<CustomColUiDto> additional = List.of(additionalColumn("techniqueDeFabrication"), additionalColumn("nombreDeTessons"));

        FormUiDto composed = CustomFormComposer.withAdditionalFields(base, "Champs additionnels", additional);

        assertThat(composed.getLayout()).hasSize(2);
        CustomFormPanelUiDto additionalPanel = composed.getLayout().get(1);
        assertThat(additionalPanel.getName()).isEqualTo("Champs additionnels");
        assertThat(additionalPanel.getIsSystemPanel()).isFalse();
        assertThat(additionalPanel.getRows()).hasSize(1);
        assertThat(additionalPanel.getRows().get(0).getColumns()).containsExactlyElementsOf(additional);
    }

    @Test
    void withAdditionalFields_shouldNotMutateTheBaseForm() {
        FormUiDto base = baseForm();
        int originalPanelCount = base.getLayout().size();
        CustomFormPanelUiDto originalGeneralPanel = base.getLayout().get(0);

        CustomFormComposer.withAdditionalFields(base, "Additional", List.of(additionalColumn("x")));

        assertThat(base.getLayout()).hasSize(originalPanelCount);
        assertThat(base.getLayout().get(0)).isSameAs(originalGeneralPanel);
    }

    @Test
    void withAdditionalFields_shouldReturnANewFormInstance() {
        FormUiDto base = baseForm();

        FormUiDto composed = CustomFormComposer.withAdditionalFields(base, "Additional", List.of(additionalColumn("x")));

        assertThat(composed).isNotSameAs(base);
    }

    @Test
    void withAdditionalFields_shouldNotMutateAnUnrelatedListPassedAsBaseLayout() {
        FormUiDto base = baseForm();
        List<CustomFormPanelUiDto> layoutSnapshot = new ArrayList<>(base.getLayout());

        CustomFormComposer.withAdditionalFields(base, "Additional", List.of(additionalColumn("x")));

        assertThat(base.getLayout()).isEqualTo(layoutSnapshot);
    }

    @Test
    void withoutFields_shouldReturnBaseFormUnchangedWhenNothingToRemove() {
        FormUiDto base = baseForm();

        assertThat(CustomFormComposer.withoutFields(base, Set.of())).isSameAs(base);
        assertThat(CustomFormComposer.withoutFields(base, null)).isSameAs(base);
    }

    @Test
    void withoutFields_shouldRemoveMatchingColumnAndKeepOthersInTheSameRow() {
        FormUiDto base = baseFormWithTwoRows();

        FormUiDto result = CustomFormComposer.withoutFields(base, Set.of("description"));

        List<CustomColUiDto> firstRowColumns = result.getLayout().get(0).getRows().get(0).getColumns();
        assertThat(firstRowColumns).extracting(c -> c.getField().getValueBinding()).containsExactly("name");
        assertThat(result.getLayout().get(0).getRows()).hasSize(2);
    }

    @Test
    void withoutFields_shouldDropRowsLeftEmptyByRemoval() {
        FormUiDto base = baseFormWithTwoRows();

        FormUiDto result = CustomFormComposer.withoutFields(base, Set.of("extra"));

        assertThat(result.getLayout().get(0).getRows()).hasSize(1);
        assertThat(result.getLayout().get(0).getRows().get(0).getColumns())
                .extracting(c -> c.getField().getValueBinding())
                .containsExactly("name", "description");
    }

    @Test
    void withoutFields_shouldNotMutateTheBaseForm() {
        FormUiDto base = baseFormWithTwoRows();
        int originalRowCount = base.getLayout().get(0).getRows().size();

        CustomFormComposer.withoutFields(base, Set.of("extra", "description"));

        assertThat(base.getLayout().get(0).getRows()).hasSize(originalRowCount);
        assertThat(base.getLayout().get(0).getRows().get(0).getColumns()).hasSize(2);
    }

    @Test
    void withFieldsInPanel_shouldHandBackAnIndependentMutableCopyOfTheNamedPanelEvenWithNoColumns() {
        FormUiDto base = baseForm();

        FormUiDto composedFromEmptyList = CustomFormComposer.withFieldsInPanel(base, "General", List.of());
        FormUiDto composedFromNull = CustomFormComposer.withFieldsInPanel(base, "General", null);

        // Not the same instances: a caller may go on to add fields to the named panel in place
        // (e.g. a field created "on the fly"), and the base form may be a shared static singleton.
        assertThat(composedFromEmptyList).isNotSameAs(base);
        assertThat(composedFromNull).isNotSameAs(base);
        CustomFormPanelUiDto panel = composedFromEmptyList.getLayout().get(0);
        assertThat(panel.getRows()).isNotSameAs(base.getLayout().get(0).getRows());
        assertThat(panel.getRows().get(0).getColumns())
                .isNotSameAs(base.getLayout().get(0).getRows().get(0).getColumns())
                .isEqualTo(base.getLayout().get(0).getRows().get(0).getColumns());

        // The copy is mutable: a caller can safely add a row/column to it in place
        assertThatCode(() -> panel.getRows().add(new CustomRowUiDto())).doesNotThrowAnyException();
    }

    @Test
    void withFieldsInPanel_shouldAppendATrailingRowToTheNamedPanel() {
        FormUiDto base = baseFormWithTwoRows();
        List<CustomColUiDto> columns = List.of(additionalColumn("profondeur"));

        FormUiDto composed = CustomFormComposer.withFieldsInPanel(base, "General", columns);

        CustomFormPanelUiDto panel = composed.getLayout().get(0);
        assertThat(panel.getRows()).hasSize(3);
        assertThat(panel.getRows().get(2).getColumns()).containsExactlyElementsOf(columns);
        assertThat(panel.getName()).isEqualTo("General");
        assertThat(panel.getIsSystemPanel()).isTrue();
    }

    @Test
    void withFieldsInPanel_shouldLeaveTheFormAloneWhenThePanelIsUnknown() {
        FormUiDto base = baseFormWithTwoRows();

        FormUiDto composed = CustomFormComposer.withFieldsInPanel(base, "Mesures", List.of(additionalColumn("profondeur")));

        assertThat(composed.getLayout().get(0).getRows()).hasSize(2);
    }

    @Test
    void withFieldsInPanel_shouldNotMutateTheBaseForm() {
        FormUiDto base = baseFormWithTwoRows();
        CustomFormPanelUiDto originalPanel = base.getLayout().get(0);
        int originalRowCount = originalPanel.getRows().size();

        CustomFormComposer.withFieldsInPanel(base, "General", List.of(additionalColumn("profondeur")));

        assertThat(base.getLayout().get(0)).isSameAs(originalPanel);
        assertThat(originalPanel.getRows()).hasSize(originalRowCount);
    }
}
