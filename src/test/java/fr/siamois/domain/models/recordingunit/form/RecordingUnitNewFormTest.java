package fr.siamois.domain.models.recordingunit.form;

import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingUnitNewFormTest {

    @Test
    void build_shouldReturnOnePanelWithFiveFields() {
        FormUiDto form = RecordingUnitNewForm.build();

        assertThat(form.getLayout()).hasSize(1);
        List<CustomColUiDto> columns = form.getLayout().get(0).getRows().get(0).getColumns();
        assertThat(columns).hasSize(5);
        assertThat(columns.stream().map(c -> c.getField().getValueBinding()).toList())
                .containsExactly("actionUnit", "spatialUnit", "author", "type", "openingDate");
    }

    @Test
    void build_shouldMarkActionUnitReadOnlyAndRequired() {
        FormUiDto form = RecordingUnitNewForm.build();

        CustomColUiDto actionUnitCol = form.getLayout().get(0).getRows().get(0).getColumns().get(0);
        assertThat(actionUnitCol.isReadOnly()).isTrue();
        assertThat(actionUnitCol.isRequired()).isTrue();
    }

    @Test
    void build_shouldReturnANewInstanceEachTime() {
        assertThat(RecordingUnitNewForm.build()).isNotSameAs(RecordingUnitNewForm.build());
    }
}
