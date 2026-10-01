package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * The catalog carries rules only for the entities whose form is fixed (projects, places). A configurable
 * table's rules belong to a (project, type) and are read from the project's per-type forms.
 */
class FieldQueryServiceRulesTest {

    private final FieldQueryService service = new FieldQueryService(
            mock(fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository.class),
            mock(jakarta.persistence.EntityManager.class, RETURNS_DEEP_STUBS));

    private static CustomField ruField(String binding) {
        return SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, binding);
    }

    @Test
    void aConfigurableTableCarriesNoCatalogRules() {
        assertThat(service.rulesOf(RecordingUnit.class, ruField("erosionShape"))).isSameAs(FieldRules.NONE);
        assertThat(service.rulesOf(RecordingUnit.class, ruField("closingDate"))).isSameAs(FieldRules.NONE);
    }

    @Test
    void aProjectFieldCarriesTheRulesOfItsFixedForm() {
        boolean any = ActionUnit.DETAILS_FORM.getLayout().stream()
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .anyMatch(col -> !service.rulesOf(ActionUnit.class, col.getField()).isEmpty());

        assertThat(any).isTrue();
        assertThat(service.rulesOf(ActionUnit.class, null)).isSameAs(FieldRules.NONE);
    }

    @Test
    void withQueryLeavesAConfigurableTableEntryWithoutRules() {
        CustomField erosionShape = ruField("erosionShape");
        FieldResource resource = FieldAnswerWireService.fieldResourceOf(erosionShape, "Forme", null);

        assertThat(service.withQuery(resource, RecordingUnit.class, erosionShape).rules()).isNull();
    }
}
