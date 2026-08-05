package fr.siamois.domain.models.recordingunit.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingUnitDetailsFormTest {

    @Test
    void build_shouldReturnFourPanels() {
        FormUiDto form = RecordingUnitDetailsForm.build();

        assertThat(form.getLayout()).hasSize(4);
        assertThat(form.getLayout().stream().map(CustomFormPanelUiDto::getName))
                .containsExactly(
                        "common.header.general",
                        "recordingunit.panel.chronology",
                        "recordingunit.panel.measurements",
                        "common.header.general"
                );
    }

    @Test
    void build_generalPanel_shouldContainExpectedFieldsInOrder() {
        FormUiDto form = RecordingUnitDetailsForm.build();
        CustomFormPanelUiDto generalPanel = form.getLayout().get(0);

        List<String> firstRowBindings = allColumns(generalPanel).stream()
                .map(c -> c.getField().getValueBinding())
                .toList();

        assertThat(firstRowBindings).containsSubsequence(
                "spatialUnit", "parents", "children", "fullIdentifier", "type",
                "geomorphologicalCycle", "geomorphologicalAgent", "normalizedInterpretation",
                "erosionShape", "erosionProfile", "erosionOrientation",
                "description", "comments"
        );
    }

    @Test
    void build_erosionColumns_shouldCarryEnabledWhenSpecOnNatureField() {
        FormUiDto form = RecordingUnitDetailsForm.build();
        CustomFormPanelUiDto generalPanel = form.getLayout().get(0);

        List<CustomColUiDto> erosionCols = allColumns(generalPanel).stream()
                .filter(c -> c.getField().getValueBinding().startsWith("erosion"))
                .toList();

        assertThat(erosionCols).hasSize(3);
        CustomField natureField = allColumns(generalPanel).stream()
                .map(CustomColUiDto::getField)
                .filter(f -> "geomorphologicalCycle".equals(f.getValueBinding()))
                .findFirst().orElseThrow();

        for (CustomColUiDto col : erosionCols) {
            assertThat(col.getEnabledWhenSpec()).isNotNull();
            assertThat(col.getEnabledWhenSpec().getFieldId()).isEqualTo(natureField.getId());
        }
    }

    @Test
    void build_interpretationColumn_shouldDependOnNatureField() {
        FormUiDto form = RecordingUnitDetailsForm.build();
        CustomFormPanelUiDto generalPanel = form.getLayout().get(0);

        CustomColUiDto interpretationCol = allColumns(generalPanel).stream()
                .filter(c -> "normalizedInterpretation".equals(c.getField().getValueBinding()))
                .findFirst().orElseThrow();
        CustomField natureField = allColumns(generalPanel).stream()
                .map(CustomColUiDto::getField)
                .filter(f -> "geomorphologicalCycle".equals(f.getValueBinding()))
                .findFirst().orElseThrow();

        assertThat(interpretationCol.getDependsOnSpec()).isNotNull();
        assertThat(interpretationCol.getDependsOnSpec().getFieldId()).isEqualTo(natureField.getId());
    }

    @Test
    void build_measurementsPanel_shouldAllowUserAddedFields() {
        FormUiDto form = RecordingUnitDetailsForm.build();
        CustomFormPanelUiDto measurementsPanel = form.getLayout().get(2);

        assertThat(measurementsPanel.getCanUserAddFields()).isTrue();
        assertThat(allColumns(measurementsPanel).stream().map(c -> c.getField().getValueBinding()))
                .containsExactly("zInf", "zSup");
    }

    @Test
    void build_shouldNotIncludeUnwiredMatrixFields() {
        FormUiDto form = RecordingUnitDetailsForm.build();

        boolean hasMatrixField = form.getLayout().stream()
                .flatMap(p -> allColumns(p).stream())
                .map(c -> c.getField().getValueBinding())
                .anyMatch(binding -> binding != null && binding.startsWith("matrix"));

        assertThat(hasMatrixField).isFalse();
    }

    private static List<CustomColUiDto> allColumns(CustomFormPanelUiDto panel) {
        return panel.getRows().stream().flatMap(r -> r.getColumns().stream()).toList();
    }
}
