package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThesaurusServersTest {

    @Test
    void normalize_dropsTheTrailingSlashAndTheSpaces() {
        assertThat(ThesaurusServers.normalize(" https://thesaurus.example/ ")).isEqualTo("https://thesaurus.example");
    }

    @Test
    void known_listsTheDefaultFirst_thenTheInstancesOfTheKnownThesauri_withoutDuplicates() {
        VocabularyService service = mock(VocabularyService.class);
        Vocabulary same = new Vocabulary();
        same.setBaseUri("https://default.example/");
        Vocabulary other = new Vocabulary();
        other.setBaseUri("https://other.example");
        when(service.findAll()).thenReturn(List.of(same, other));

        assertThat(ThesaurusServers.known(service, "https://default.example")).containsExactly("https://default.example", "https://other.example");
    }

    @Test
    void matching_keepsTheInstancesContainingWhatWasTyped() {
        assertThat(ThesaurusServers.matching(List.of("https://a.example", "https://b.example"), " B.EX ")).containsExactly("https://b.example");
        assertThat(ThesaurusServers.matching(List.of("https://a.example"), null)).hasSize(1);
    }

    @Test
    void matchingThesauri_searchesTheLabelAndTheIdentifier() {
        List<ThesaurusInfo> thesauri = List.of(new ThesaurusInfo("s", "th10", "Céramique", "fr"), new ThesaurusInfo("s", "th11", "Faune", "fr"));

        assertThat(ThesaurusServers.matchingThesauri(thesauri, "faun")).extracting(ThesaurusInfo::idTheso).containsExactly("th11");
        assertThat(ThesaurusServers.matchingThesauri(thesauri, "th10")).extracting(ThesaurusInfo::label).containsExactly("Céramique");
    }
}
