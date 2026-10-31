package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProjectDefinitionTest {

    private final DefaultProjectDefinition definition = new DefaultProjectDefinition();

    @Test
    void readsTheThesaurusAndItsIdentifiers() {
        assertThat(definition.thesaurusUrl()).isEqualTo("https://thesaurus.mom.fr/?idt=th338");
        assertThat(definition.baseUri()).isEqualTo("https://thesaurus.mom.fr");
        assertThat(definition.externalVocabularyId()).isEqualTo("th338");
    }

    @Test
    void everyTableIsConfiguredByTheCodeItsTypeIsReadFrom() {
        assertThat(definition.tables()).isNotEmpty().allSatisfy(table -> {
            assertThat(table.typeCode()).isEqualTo(table.table().getFieldCode());
            assertThat(table.types()).isNotEmpty();
        });
        assertThat(definition.tables()).extracting(DefaultProjectDefinition.Table::table)
                .doesNotContain(ConfigurableTable.PROJECT);
    }

    @Test
    void recordingUnitsListUsFaitAndEnsembleFirst_eachWithItsOwnIdentifierFormat() {
        DefaultProjectDefinition.Table ue = definition.tables().stream()
                .filter(table -> table.table() == ConfigurableTable.UE).findFirst().orElseThrow();

        assertThat(ue.types()).extracting(DefaultProjectDefinition.Type::label).startsWith("US", "Fait", "Ensemble");
        assertThat(ue.types()).extracting(DefaultProjectDefinition.Type::identifierFormat)
                .startsWith("US{NUM_UE:000}", "F{NUM_UE:000}", "ENS{NUM_UE:000}");
    }

    @Test
    void theDefaultsAreConceptIdentifiersOfFieldsThatHaveACode() {
        assertThat(definition.tables().stream().flatMap(table -> table.types().stream())
                .flatMap(type -> type.defaults().entrySet().stream())).isNotEmpty().allSatisfy(entry -> {
            assertThat(entry.getKey()).matches("^SI[A-Z]+\\.[A-Z]+$");
            assertThat(entry.getValue()).matches("\\d+");
        });
    }

    @Test
    void recordingUnitsThatGroupOthersStartWithTheEnglobingNature() {
        DefaultProjectDefinition.Table ue = definition.tables().stream()
                .filter(table -> table.table() == ConfigurableTable.UE).findFirst().orElseThrow();

        assertThat(ue.types()).filteredOn(type -> type.label().equals("Fait") || type.label().equals("Ensemble"))
                .hasSize(2).allSatisfy(type -> assertThat(type.defaults()).containsEntry("SIARU.NATURE", "1217"));
        assertThat(ue.types().get(0).defaults()).doesNotContainKey("SIARU.NATURE");
    }

    @Test
    void everyTypeHasItsOwnIdentifierPrefix_uniqueInItsTable() {
        assertThat(definition.tables()).allSatisfy(table -> {
            assertThat(table.types()).allSatisfy(type -> assertThat(type.identifierFormat())
                    .matches("^[A-Z0-9]+\\{NUM_[A-Z_]+:0+\\}$"));
            assertThat(table.types()).extracting(type -> type.identifierFormat().substring(0, type.identifierFormat().indexOf('{'))).doesNotHaveDuplicates();
        });
    }
}
