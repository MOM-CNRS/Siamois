package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A catalog field is read-only where its declaration says so — what a list cell reads, having no
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
        CustomField editable = SystemFieldCatalog.specsOf(ConfigurableTable.UE).stream()
                .filter(spec -> !spec.readOnly())
                .map(fr.siamois.domain.models.form.config.SystemFieldSpec::field)
                .findFirst()
                .orElseThrow();

        assertThat(FieldQueryService.isReadOnly(RecordingUnit.class, editable)).isFalse();
    }

    @Test
    void everyReadOnlyColumnOfAPhaseIsReadOnly() {
        SystemFieldCatalog.specsOf(ConfigurableTable.PHASE).stream()
                .filter(fr.siamois.domain.models.form.config.SystemFieldSpec::readOnly)
                .forEach(spec -> assertThat(FieldQueryService.isReadOnly(Phase.class, spec.field())).isTrue());
    }

    @Test
    void anUnknownEntityTypeOrAFieldWithoutIdIsNeverReadOnly() {
        CustomField project = SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "actionUnit");

        assertThat(FieldQueryService.isReadOnly(String.class, project)).isFalse();
        assertThat(FieldQueryService.isReadOnly(RecordingUnit.class, null)).isFalse();
    }
}
