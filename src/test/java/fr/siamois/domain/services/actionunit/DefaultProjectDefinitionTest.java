package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProjectDefinitionTest {

    private final DefaultProjectDefinition definition = new DefaultProjectDefinition();

    @Test
    void readsTheThesaurusAndItsIdentifiers() {
        assertThat(definition.thesaurusUrl()).isEqualTo("https://thesaurus.mom.fr/?idt=th332");
        assertThat(definition.baseUri()).isEqualTo("https://thesaurus.mom.fr");
        assertThat(definition.externalVocabularyId()).isEqualTo("th332");
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
    void recordingUnitsStartWithUsFaitAndEnsemble_eachWithItsOwnIdentifierFormat() {
        DefaultProjectDefinition.Table ue = definition.tables().stream()
                .filter(table -> table.table() == ConfigurableTable.UE).findFirst().orElseThrow();

        assertThat(ue.types()).extracting(DefaultProjectDefinition.Type::label).containsExactly("US", "Fait", "Ensemble");
        assertThat(ue.types()).extracting(DefaultProjectDefinition.Type::identifierFormat)
                .containsExactly("US{NUM_UE:000}", "F{NUM_UE:000}", "ENS{NUM_UE:000}");
        assertThat(ue.types().get(0).lists()).containsKeys("SIARU.GEOMORPHO", "SIARU.INTERPRETATION");
        assertThat(ue.types().get(0).listNames()).containsOnlyKeys(ue.types().get(0).lists().keySet().toArray(String[]::new));
    }

    @Test
    void collectionIdsAreEachListedOnce() {
        assertThat(definition.collectionIds()).isNotEmpty().allMatch(id -> id.matches("g\\d+"));
        assertThat(definition.collectionIds()).doesNotHaveDuplicates();
    }
}
