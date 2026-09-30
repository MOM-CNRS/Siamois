package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.rules.ConceptIdLookup;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * A catalog field carries the conditional rules of its column in the details form — what a list
 * cell reads, having no layout of its own.
 */
class FieldQueryServiceRulesTest {

    private static CustomField ruField(String binding) {
        return SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, binding);
    }

    @Test
    void anErosionFieldCarriesItsEnabledWhen() {
        FieldRules rules = FieldQueryService.rulesOf(RecordingUnit.class, ruField("erosionShape"));

        assertThat(rules.enabledWhen()).isNotNull();
    }

    @Test
    void theInterpretationCarriesItsRelatedConceptsFilter() {
        FieldRules rules = FieldQueryService.rulesOf(RecordingUnit.class, ruField("normalizedInterpretation"));

        assertThat(rules.options()).isInstanceOf(OptionsFilter.RelatedConcepts.class);
    }

    @Test
    void theClosingDateCarriesItsOrderingConstraint() {
        FieldRules rules = FieldQueryService.rulesOf(RecordingUnit.class, ruField("closingDate"));

        assertThat(rules.constraints()).hasSize(1);
        assertThat(rules.constraints().get(0).fieldId()).isEqualTo(ruField("openingDate").getId());
    }

    @Test
    void aFieldWithoutRulesHasNone() {
        assertThat(FieldQueryService.rulesOf(RecordingUnit.class, ruField("comments")).isEmpty()).isTrue();
        assertThat(FieldQueryService.rulesOf(ActionUnit.class, null)).isSameAs(FieldRules.NONE);
    }

    @Test
    void withQueryPutsTheWireRulesOnTheCatalogEntry() {
        FieldQueryService service = new FieldQueryService(
                mock(fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository.class),
                mock(jakarta.persistence.EntityManager.class, RETURNS_DEEP_STUBS), (voc, concept) -> Optional.of(77L));
        CustomField erosionShape = ruField("erosionShape");
        FieldResource resource = FieldAnswerWireService.fieldResourceOf(erosionShape, "Forme", null);

        FieldResource withRules = service.withQuery(resource, RecordingUnit.class, erosionShape);

        assertThat(withRules.rules()).containsKey("enabledWhen");
        assertThat(service.withQuery(FieldAnswerWireService.fieldResourceOf(ruField("comments"), "c", null),
                RecordingUnit.class, ruField("comments")).rules()).isNull();
    }
}
