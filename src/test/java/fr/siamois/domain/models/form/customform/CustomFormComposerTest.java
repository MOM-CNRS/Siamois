package fr.siamois.domain.models.form.customform;

import fr.siamois.domain.models.form.customfield.CustomFieldText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomFormComposerTest {

    private CustomForm baseForm() {
        return new CustomForm.Builder()
                .name("Base form")
                .description("A base form")
                .addPanel(new CustomFormPanel.Builder()
                        .name("General")
                        .isSystemPanel(true)
                        .addRow(new CustomRow.Builder()
                                .addColumn(new CustomCol.Builder()
                                        .field(CustomFieldText.builder().id(1L).label("name").valueBinding("name").build())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private CustomCol additionalColumn(String label) {
        return new CustomCol.Builder()
                .field(CustomFieldText.builder().id(100L).label(label).valueBinding(label).build())
                .build();
    }

    @Test
    void withAdditionalFields_shouldReturnBaseFormUnchangedWhenNoAdditionalFields() {
        CustomForm base = baseForm();

        assertThat(CustomFormComposer.withAdditionalFields(base, "Additional", List.of())).isSameAs(base);
        assertThat(CustomFormComposer.withAdditionalFields(base, "Additional", null)).isSameAs(base);
    }

    @Test
    void withAdditionalFields_shouldAppendATrailingPanel() {
        CustomForm base = baseForm();
        List<CustomCol> additional = List.of(additionalColumn("techniqueDeFabrication"), additionalColumn("nombreDeTessons"));

        CustomForm composed = CustomFormComposer.withAdditionalFields(base, "Champs additionnels", additional);

        assertThat(composed.getLayout()).hasSize(2);
        CustomFormPanel additionalPanel = composed.getLayout().get(1);
        assertThat(additionalPanel.getName()).isEqualTo("Champs additionnels");
        assertThat(additionalPanel.getIsSystemPanel()).isFalse();
        assertThat(additionalPanel.getRows()).hasSize(1);
        assertThat(additionalPanel.getRows().get(0).getColumns()).containsExactlyElementsOf(additional);
    }

    @Test
    void withAdditionalFields_shouldNotMutateTheBaseForm() {
        CustomForm base = baseForm();
        int originalPanelCount = base.getLayout().size();
        CustomFormPanel originalGeneralPanel = base.getLayout().get(0);

        CustomFormComposer.withAdditionalFields(base, "Additional", List.of(additionalColumn("x")));

        assertThat(base.getLayout()).hasSize(originalPanelCount);
        assertThat(base.getLayout().get(0)).isSameAs(originalGeneralPanel);
    }

    @Test
    void withAdditionalFields_shouldReturnANewFormInstance() {
        CustomForm base = baseForm();

        CustomForm composed = CustomFormComposer.withAdditionalFields(base, "Additional", List.of(additionalColumn("x")));

        assertThat(composed).isNotSameAs(base);
        assertThat(composed.getName()).isEqualTo(base.getName());
        assertThat(composed.getDescription()).isEqualTo(base.getDescription());
    }

    @Test
    void withAdditionalFields_shouldNotMutateAnUnrelatedListPassedAsBaseLayout() {
        CustomForm base = baseForm();
        List<CustomFormPanel> layoutSnapshot = new ArrayList<>(base.getLayout());

        CustomFormComposer.withAdditionalFields(base, "Additional", List.of(additionalColumn("x")));

        assertThat(base.getLayout()).isEqualTo(layoutSnapshot);
    }
}
