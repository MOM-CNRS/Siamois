package fr.siamois.domain.models.recordingunit.form;

import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingUnitNewFormTest {

    @Test
    void build_shouldReturnOnePanelWithFiveFields() {
        CustomForm form = RecordingUnitNewForm.build();

        assertThat(form.getLayout()).hasSize(1);
        List<CustomCol> columns = form.getLayout().get(0).getRows().get(0).getColumns();
        assertThat(columns).hasSize(5);
        assertThat(columns.stream().map(c -> c.getField().getValueBinding()).collect(Collectors.toList()))
                .containsExactly("actionUnit", "spatialUnit", "author", "type", "openingDate");
    }

    @Test
    void build_shouldMarkActionUnitReadOnlyAndRequired() {
        CustomForm form = RecordingUnitNewForm.build();

        CustomCol actionUnitCol = form.getLayout().get(0).getRows().get(0).getColumns().get(0);
        assertThat(actionUnitCol.isReadOnly()).isTrue();
        assertThat(actionUnitCol.isRequired()).isTrue();
    }

    @Test
    void build_shouldReturnANewInstanceEachTime() {
        assertThat(RecordingUnitNewForm.build()).isNotSameAs(RecordingUnitNewForm.build());
    }
}
