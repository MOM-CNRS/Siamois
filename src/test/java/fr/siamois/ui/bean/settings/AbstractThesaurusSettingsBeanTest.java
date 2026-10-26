package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import fr.siamois.domain.models.vocabulary.ThesaurusUsage;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.ThesaurusUsageService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.ui.bean.LangBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractThesaurusSettingsBeanTest {

    private static final String SERVER = "https://thesaurus.example";

    @Mock
    private VocabularyService vocabularyService;
    @Mock
    private LabelService labelService;
    @Mock
    private LangBean langBean;

    private AbstractThesaurusSettingsBean bean;

    @BeforeEach
    void setUp() {
        lenient().when(langBean.getLanguageCode()).thenReturn("fr");
        bean = new AbstractThesaurusSettingsBean(vocabularyService, labelService, langBean) {
            @Override
            protected ThesaurusUsageService.RewireResult rewire(List<ThesaurusUsage> rows, Vocabulary target) {
                return new ThesaurusUsageService.RewireResult(0, 0, 0);
            }

            @Override
            protected void reloadUsages() {
                // nothing to read in this test
            }
        };
    }

    private Vocabulary vocabularyAt(String baseUri, String id) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setBaseUri(baseUri);
        vocabulary.setExternalVocabularyId(id);
        return vocabulary;
    }

    @Test
    void serverOptions_listTheDefaultInstanceAndTheOnesOfTheKnownThesauri_withoutDuplicates() {
        when(vocabularyService.findAll()).thenReturn(List.of(vocabularyAt("https://thesaurus.mom.fr/", "th1"), vocabularyAt(SERVER, "th2")));

        assertThat(bean.getServerOptions()).containsExactly("https://thesaurus.mom.fr", SERVER);
    }

    @Test
    void completeServers_filtersWhatWasTyped() {
        when(vocabularyService.findAll()).thenReturn(List.of(vocabularyAt(SERVER, "th2")));

        assertThat(bean.completeServers("example")).containsExactly(SERVER);
        assertThat(bean.completeServers("")).hasSize(2);
    }

    @Test
    void pickingAnInstance_listsItsThesauri_andTheyAreSearchedByLabelOrIdentifier() throws Exception {
        bean.setPickedServer(SERVER + "/");
        when(vocabularyService.findAllThesaurusOf(SERVER, "fr")).thenReturn(List.of(
                new ThesaurusInfo(SERVER, "th10", "Céramique", "fr"), new ThesaurusInfo(SERVER, "th11", "Faune", "fr")));

        bean.onServerPicked();

        assertThat(bean.completeThesauri("faun")).extracting(ThesaurusInfo::idTheso).containsExactly("th11");
        assertThat(bean.completeThesauri("th10")).extracting(ThesaurusInfo::label).containsExactly("Céramique");
    }

    @Test
    void addServer_registersAnInstanceThatAnswers_andPicksIt() throws Exception {
        when(vocabularyService.findAll()).thenReturn(List.of());
        when(vocabularyService.findAllThesaurusOf(SERVER, "fr")).thenReturn(List.of(new ThesaurusInfo(SERVER, "th10", "Céramique", "fr")));
        bean.setNewServerUrl(SERVER + "/");

        bean.addServer();

        assertThat(bean.getServerOptions()).contains(SERVER);
        assertThat(bean.getPickedServer()).isEqualTo(SERVER);
        assertThat(bean.getNewServerUrl()).isNull();
        assertThat(bean.completeThesauri("")).hasSize(1);
    }

    @Test
    void addThesaurus_selectsThePickedThesaurus() throws Exception {
        Vocabulary vocabulary = vocabularyAt(SERVER, "th10");
        when(vocabularyService.findOrCreateVocabularyOfUri(SERVER + "?idt=th10")).thenReturn(vocabulary);
        when(vocabularyService.findAll()).thenReturn(List.of(vocabulary));
        var label = mock(fr.siamois.domain.models.vocabulary.label.VocabularyLabel.class);
        when(label.getValue()).thenReturn("Céramique");
        when(labelService.findLabelOf(vocabulary, "fr")).thenReturn(label);
        bean.setPickedThesaurus(new ThesaurusInfo(SERVER, "th10", "Céramique", "fr"));
        assertThat(bean.isThesaurusPicked()).isTrue();

        bean.addThesaurus();

        assertThat(bean.getThesaurusUrl()).isEqualTo(SERVER + "/?idt=th10");
        assertThat(bean.getPickedThesaurus()).isNull();
        assertThat(bean.isThesaurusPicked()).isFalse();
    }

    @Test
    void addThesaurus_doesNothing_whenNoThesaurusIsPicked() {
        bean.addThesaurus();

        assertThat(bean.getThesaurusUrl()).isNull();
    }
}
