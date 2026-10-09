package fr.siamois.domain.services.form.layout;

import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.utils.TestForms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FormLayoutSeedsTest {

    @ParameterizedTest
    @EnumSource(ConfigurableTable.class)
    void everyTableHasAnInitialLayoutOfNonEmptyGroups(ConfigurableTable table) {
        FormLayout layout = TestForms.layoutOf(table);

        assertThat(layout.groups()).isNotEmpty().allSatisfy(group -> {
            assertThat(group.label()).isNotBlank();
            assertThat(group.items()).isNotEmpty();
        });
    }

    @Test
    void aRecordingUnitLaysOutItsErosionFieldsBehindTheErosionNature() {
        var erosionShape = item(ConfigurableTable.UE, "erosionShape");

        assertThat(erosionShape.rules().enabledWhen()).isNotNull();
        assertThat(erosionShape.rules().enabledWhen().toString()).contains(String.valueOf(TestForms.CONCEPT_ID));
        assertThat(erosionShape.rules().enabledWhen()).isEqualTo(
                item(ConfigurableTable.UE, "erosionProfile").rules().enabledWhen());
    }

    @Test
    void theInterpretationIsFilteredByTheRelatedConceptsOfTheNature() {
        var rules = item(ConfigurableTable.UE, "normalizedInterpretation").rules();

        assertThat(rules.options()).isInstanceOf(OptionsFilter.RelatedConcepts.class);
        assertThat(((OptionsFilter.RelatedConcepts) rules.options()).fieldId())
                .isEqualTo(SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "geomorphologicalCycle").getId());
    }

    @Test
    void theClosingDateMustNotPrecedeTheOpeningDate() {
        var rules = item(ConfigurableTable.UE, "closingDate").rules();

        assertThat(rules.constraints()).hasSize(1);
        assertThat(rules.constraints().get(0).fieldId())
                .isEqualTo(SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "openingDate").getId());
    }

    @Test
    void theRulesOfAnUnknownConceptAreDroppedRatherThanKeptUnmatchable() {
        FormLayoutSeeds seeds = new FormLayoutSeeds((vocabulary, concept) -> Optional.empty());

        var erosionShape = seeds.layoutOf(ConfigurableTable.UE).groups().stream()
                .flatMap(g -> g.items().stream())
                .filter(i -> "erosionShape".equals(i.field().getValueBinding()))
                .findFirst().orElseThrow();

        assertThat(erosionShape.rules()).isSameAs(FieldRules.NONE);
    }

    @Test
    void theSeedMarksMandatoryWhatTheOldFormRequired() {
        var required = TestForms.seeds().requiredFieldIds(ConfigurableTable.UE);

        assertThat(required).contains(
                SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "openingDate").getId(),
                SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "author").getId());
        assertThat(TestForms.layoutOf(ConfigurableTable.UE).groups().stream().flatMap(g -> g.items().stream())
                .filter(i -> required.contains(i.field().getId()))).allSatisfy(i -> assertThat(i.mandatory()).isTrue());
    }

    @Test
    void rulesOfReadsTheSameRulesWithoutBuildingTheWholeLayout() {
        long id = SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "taq").getId();

        assertThat(TestForms.seeds().rulesOf(ConfigurableTable.UE, id).constraints()).hasSize(1);
        assertThat(TestForms.seeds().rulesOf(ConfigurableTable.UE, -999L).isEmpty()).isTrue();
    }

    @Test
    void conceptValuesAreInternalIds() {
        assertThat(FieldValueSpec.concept(5L)).isEqualTo(new FieldValueSpec.ConceptValue(5L));
    }

    private FormLayout.Item item(ConfigurableTable table, String binding) {
        return TestForms.layoutOf(table).groups().stream().flatMap(g -> g.items().stream())
                .filter(i -> binding.equals(i.field().getValueBinding()))
                .findFirst().orElseThrow();
    }
}
