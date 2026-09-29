package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A catalog field is read-only where its details form says so — what a list cell reads, having no
 * layout of its own (the fiche honours the same flag through the layout).
 */
class FieldQueryServiceReadOnlyTest {

    @Test
    void theProjectOfARecordingUnitIsReadOnly() {
        CustomField project = SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "actionUnit");

        assertThat(FieldQueryService.isReadOnly(RecordingUnit.class, project)).isTrue();
    }

    @Test
    void aFieldTheDetailsFormLetsEditStaysEditable() {
        CustomField editable = SystemFieldCatalog.systemColumnsOf(ConfigurableTable.UE).stream()
                .filter(column -> !column.isReadOnly())
                .map(CustomColUiDto::getField)
                .findFirst()
                .orElseThrow();

        assertThat(FieldQueryService.isReadOnly(RecordingUnit.class, editable)).isFalse();
    }

    @Test
    void everyReadOnlyColumnOfAPhaseIsReadOnly() {
        SystemFieldCatalog.systemColumnsOf(ConfigurableTable.PHASE).stream()
                .filter(CustomColUiDto::isReadOnly)
                .forEach(column -> assertThat(FieldQueryService.isReadOnly(Phase.class, column.getField())).isTrue());
    }

    @Test
    void anUnknownEntityTypeOrAFieldWithoutIdIsNeverReadOnly() {
        CustomField project = SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "actionUnit");

        assertThat(FieldQueryService.isReadOnly(String.class, project)).isFalse();
        assertThat(FieldQueryService.isReadOnly(RecordingUnit.class, null)).isFalse();
    }
}
