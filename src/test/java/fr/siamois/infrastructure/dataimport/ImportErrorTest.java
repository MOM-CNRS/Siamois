package fr.siamois.infrastructure.dataimport;

import fr.siamois.infrastructure.database.initializer.seeder.MissingConceptException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportErrorTest {

    @Test
    void forRow_aColumnPrefix_isSplitOutOfTheMessage() {
        ImportError error = ImportError.forRow("UE", 4, "[colonne 'Date'] : invalide");

        assertThat(error.column()).isEqualTo("Date");
        assertThat(error.message()).isEqualTo("invalide");
        assertThat(error.isVocabularyError()).isFalse();
    }

    @Test
    void forRow_noPrefix_keepsTheRawMessage() {
        ImportError error = ImportError.forRow("UE", 4, "boom");

        assertThat(error.column()).isEmpty();
        assertThat(error.message()).isEqualTo("boom");
    }

    @Test
    void forRow_aVocabularyFailure_carriesTheFieldAndTheTypeOfTheRow() {
        var failure = new RuntimeException("wrapper", new VocabularyResolutionException("SIARU.NATURE", "[colonne 'Nature'] : introuvable", null));

        ImportError error = ImportError.forRow("UE", 4, failure, "US");

        assertThat(error.isVocabularyError()).isTrue();
        assertThat(error.vocabulary().fieldCode()).isEqualTo("SIARU.NATURE");
        assertThat(error.vocabulary().typeLabel()).isEqualTo("US");
    }

    @Test
    void forTable_aMissingConcept_carriesItsAddress() {
        var failure = new RuntimeException("save", new MissingConceptException("manquant", "https://thesaurus.mom.fr/?idc=1&idt=th337"));

        ImportError error = ImportError.forTable("recording_unit", failure, "manquant");

        assertThat(error.vocabulary().conceptUrl()).isEqualTo("https://thesaurus.mom.fr/?idc=1&idt=th337");
        assertThat(error.vocabulary().fieldCode()).isNull();
    }

    @Test
    void forTable_anotherFailure_isNotAVocabularyError() {
        assertThat(ImportError.forTable("recording_unit", new RuntimeException("x"), "x").isVocabularyError()).isFalse();
    }
}
