package fr.siamois.domain.services.form.layout;

import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.OptionsFilter;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.utils.TestForms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FormLayoutSeedsTest {

    @ParameterizedTest
    @EnumSource(value = ConfigurableTable.class, mode = EnumSource.Mode.EXCLUDE, names = "PROJECT")
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

    /** The interpretation is not tied to another field's answer: its list follows the type of the record. */
    @Test
    void theInterpretationFollowsTheTypeOfTheRecordAndNoOtherField() {
        var rules = item(ConfigurableTable.UE, "normalizedInterpretation").rules();

        assertThat(rules.options()).isInstanceOf(OptionsFilter.RelatedToType.class);
        assertThat(rules.enabledWhen()).isNull();
    }

    @Test
    void theListsTheThesaurusRelatesToATypeFollowTheTypeOfTheRecord() {
        assertThat(item(ConfigurableTable.UE, "nature").rules().options()).isInstanceOf(OptionsFilter.RelatedToType.class);
        assertThat(item(ConfigurableTable.UE, "geomorphologicalCycle").rules().options()).isInstanceOf(OptionsFilter.RelatedToType.class);
        for (String binding : new String[]{"material", "materialClass", "normalizedInterpretation", "sanitaryAlert"}) {
            assertThat(item(ConfigurableTable.MOBILIER, binding).rules().options()).as(binding).isInstanceOf(OptionsFilter.RelatedToType.class);
        }
        for (String binding : new String[]{"category", "supportNatures", "format"}) {
            assertThat(item(ConfigurableTable.DOCUMENT, binding).rules().options()).as(binding).isInstanceOf(OptionsFilter.RelatedToType.class);
        }
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

    // ---- defaults ----

    /** The initial layout of recording units with one field carrying the given {@code "default"} JSON (null: none). */
    @SuppressWarnings("unchecked")
    private static FormLayout layoutWithDefault(FormLayoutSeeds seeds, String defaultJson) throws Exception {
        long fieldId = SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, "openingDate").getId();
        String field = "{\"fieldId\":" + fieldId + (defaultJson == null ? "" : ",\"default\":" + defaultJson) + "}";
        JsonNode tree = new ObjectMapper().readTree("{\"groups\":[{\"label\":\"G\",\"fields\":[" + field + "]}]}");
        ((Map<ConfigurableTable, JsonNode>) ReflectionTestUtils.getField(seeds, "raw")).put(ConfigurableTable.UE, tree);
        return seeds.layoutOf(ConfigurableTable.UE);
    }

    private static fr.siamois.domain.models.form.config.FieldDefault defaultOf(FormLayout layout) {
        return layout.groups().get(0).items().get(0).defaultValue();
    }

    private static final String CITED_CONCEPT = "{\"concepts\":[{\"vocabularyExtId\":\"th\",\"conceptExtId\":\"1\"}]}";

    @Test
    void aFieldDefaultCitesItsConceptsLikeTheRulesDo() throws Exception {
        FormLayoutSeeds seeds = new FormLayoutSeeds((vocabulary, concept) -> Optional.of(TestForms.CONCEPT_ID));

        assertThat(defaultOf(layoutWithDefault(seeds, CITED_CONCEPT)).conceptIds())
                .isEqualTo(List.of(String.valueOf(TestForms.CONCEPT_ID)));
    }

    @Test
    void aDefaultCitingAConceptThisInstanceLacksIsDroppedWhole() throws Exception {
        FormLayoutSeeds seeds = new FormLayoutSeeds((vocabulary, concept) -> Optional.empty());

        assertThat(defaultOf(layoutWithDefault(seeds, CITED_CONCEPT))).isNull();
    }

    @Test
    void anEmptyOrMissingDefaultIsNoDefault() throws Exception {
        FormLayoutSeeds seeds = new FormLayoutSeeds((vocabulary, concept) -> Optional.of(TestForms.CONCEPT_ID));

        assertThat(defaultOf(layoutWithDefault(seeds, "{\"concepts\":[]}"))).isNull();
        assertThat(defaultOf(layoutWithDefault(seeds, "null"))).isNull();
        assertThat(defaultOf(layoutWithDefault(seeds, null))).isNull();
    }
}
