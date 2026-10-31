package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.settings.DocumentationThesaurus;
import fr.siamois.domain.models.settings.ProjectCodeSource;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptFieldConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ProjectCodeSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldCodeResolverTest {

    private static final long INSTITUTION = 12L;
    private static final long PROJECT = 42L;
    private static final String CODE = "SIAAU.TYPE";

    @Mock
    private SiamoisThesaurus siamoisThesaurus;
    @Mock
    private DocumentationThesaurusRepository documentationThesaurusRepository;
    @Mock
    private ConceptFieldConfigRepository conceptFieldConfigRepository;
    @Mock
    private ProjectCodeSourceRepository projectCodeSourceRepository;

    @InjectMocks
    private FieldCodeResolver resolver;

    private Vocabulary siamois;
    private Vocabulary institution;
    private Vocabulary project;

    @BeforeEach
    void setUp() {
        siamois = vocabulary(1L);
        institution = vocabulary(2L);
        project = vocabulary(3L);
        lenient().when(siamoisThesaurus.vocabulary()).thenReturn(Optional.of(siamois));
        lenient().when(documentationThesaurusRepository.findOfInstitution(INSTITUTION)).thenReturn(Optional.of(documentation(institution)));
        lenient().when(documentationThesaurusRepository.findByActionUnitId(PROJECT)).thenReturn(Optional.of(documentation(project)));
        lenient().when(conceptFieldConfigRepository.findOneByVocabularyIdAndFieldCode(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
    }

    private static Vocabulary vocabulary(long id) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setId(id);
        return vocabulary;
    }

    private static DocumentationThesaurus documentation(Vocabulary vocabulary) {
        DocumentationThesaurus thesaurus = new DocumentationThesaurus();
        thesaurus.setVocabulary(vocabulary);
        return thesaurus;
    }

    private ConceptFieldConfig defined(Vocabulary in, String code) {
        ConceptFieldConfig config = new ConceptFieldConfig();
        config.setVocabulary(in);
        config.setFieldCode(code);
        lenient().when(conceptFieldConfigRepository.findOneByVocabularyIdAndFieldCode(in.getId(), code)).thenReturn(Optional.of(config));
        return config;
    }

    @Test
    void find_readsTheSiamoisThesaurus_whenItDefinesTheCode_evenIfTheProjectAndTheInstitutionDoToo() {
        ConceptFieldConfig system = defined(siamois, CODE);
        defined(institution, CODE);
        defined(project, CODE);

        assertThat(resolver.find(INSTITUTION, PROJECT, CODE)).contains(system);
    }

    @Test
    void find_readsTheProjectThesaurus_beforeTheInstitutionOne_forACodeSiamoisDoesNotDefine() {
        defined(institution, "OWN.CODE");
        ConceptFieldConfig own = defined(project, "OWN.CODE");

        assertThat(resolver.find(INSTITUTION, PROJECT, "OWN.CODE")).contains(own);
    }

    @Test
    void find_readsTheInstitutionThesaurus_whenTheProjectHasNone() {
        ConceptFieldConfig own = defined(institution, "OWN.CODE");

        assertThat(resolver.find(INSTITUTION, null, "OWN.CODE")).contains(own);
    }

    @Test
    void find_isEmpty_whenNoThesaurusDefinesTheCode() {
        assertThat(resolver.find(INSTITUTION, PROJECT, "UNKNOWN")).isEmpty();
    }

    @Test
    void find_looksACodeUpOnce_withinOneReading() {
        ConceptFieldConfig own = defined(institution, "OWN.CODE");

        FieldCodeResolver.withinOneReading(() -> {
            assertThat(resolver.find(INSTITUTION, null, "OWN.CODE")).contains(own);
            assertThat(resolver.find(INSTITUTION, null, "OWN.CODE")).contains(own);
            // a reading started within another one shares it
            return FieldCodeResolver.withinOneReading(() -> resolver.find(INSTITUTION, null, "OWN.CODE"));
        });

        verify(conceptFieldConfigRepository, times(1)).findOneByVocabularyIdAndFieldCode(institution.getId(), "OWN.CODE");
    }

    @Test
    void find_looksACodeUpAgain_onceTheReadingIsOver() {
        defined(institution, "OWN.CODE");

        FieldCodeResolver.withinOneReading(() -> resolver.find(INSTITUTION, null, "OWN.CODE"));
        resolver.find(INSTITUTION, null, "OWN.CODE");

        verify(conceptFieldConfigRepository, times(2)).findOneByVocabularyIdAndFieldCode(institution.getId(), "OWN.CODE");
    }

    @Test
    void find_stillReadsTheInstitutionThesaurus_whenSiamoisIsNotLoadedYet() {
        when(siamoisThesaurus.vocabulary()).thenReturn(Optional.empty());
        ConceptFieldConfig own = defined(institution, CODE);

        assertThat(resolver.find(INSTITUTION, null, CODE)).contains(own);
    }

    @Test
    void findIn_readsOnlyTheGivenThesaurus() {
        defined(siamois, CODE);
        ConceptFieldConfig own = defined(project, CODE);

        assertThat(resolver.findIn(project, CODE)).contains(own);
    }

    @Test
    void findIn_readsASideOfTheStratigraphyThroughTheSharedList_untilTheThesaurusDefinesIt() {
        ConceptFieldConfig shared = defined(siamois, RecordingUnit.STRATI_FIELD_CODE);

        assertThat(resolver.findIn(siamois, RecordingUnit.ANTERIOR_FIELD_CODE)).contains(shared);
    }

    @Test
    void findIn_prefersTheOwnListOfASideOfTheStratigraphy() {
        defined(siamois, RecordingUnit.STRATI_FIELD_CODE);
        ConceptFieldConfig own = defined(siamois, RecordingUnit.ANTERIOR_FIELD_CODE);

        assertThat(resolver.findIn(siamois, RecordingUnit.ANTERIOR_FIELD_CODE)).contains(own);
    }

    private ProjectCodeSource redirection() {
        ProjectCodeSource redirection = new ProjectCodeSource();
        redirection.setFieldCode(CODE);
        lenient().when(projectCodeSourceRepository.findByActionUnitIdAndFieldCode(PROJECT, CODE)).thenReturn(Optional.of(redirection));
        return redirection;
    }

    @Test
    void find_readsWhereTheProjectRedirectsTheCode_beforeTheDefaultSources() {
        defined(siamois, CODE);
        ProjectCodeSource redirection = redirection();
        redirection.setVocabulary(project);
        redirection.setSourceFieldCode("OWN.TYPE");
        ConceptFieldConfig own = defined(project, "OWN.TYPE");

        assertThat(resolver.find(INSTITUTION, PROJECT, CODE)).contains(own);
    }

    @Test
    void find_readsTheBranchTheProjectRedirectsTheCodeTo() {
        defined(siamois, CODE);
        Concept branch = new Concept();
        branch.setVocabulary(project);
        redirection().setBranchTopTerm(branch);

        ConceptFieldConfig found = resolver.find(INSTITUTION, PROJECT, CODE).orElseThrow();

        assertThat(found.getConcept()).isSameAs(branch);
        assertThat(found.getFieldCode()).isEqualTo(CODE);
    }

    @Test
    void find_fallsBackOnTheDefaultSources_whenTheRedirectionPointsAtACodeTheThesaurusNoLongerDefines() {
        ConceptFieldConfig system = defined(siamois, CODE);
        ProjectCodeSource redirection = redirection();
        redirection.setVocabulary(project);
        redirection.setSourceFieldCode("GONE");

        assertThat(resolver.find(INSTITUTION, PROJECT, CODE)).contains(system);
    }

    @Test
    void find_ignoresTheRedirectionsOfOtherProjects_whenNoProjectIsGiven() {
        ConceptFieldConfig system = defined(siamois, CODE);
        redirection().setBranchTopTerm(new Concept());

        assertThat(resolver.find(INSTITUTION, null, CODE)).contains(system);
    }

    @Test
    void findDefaultVocabulary_isTheFirstDefaultSourceThatDefinesTheCode_whateverTheProjectRedirects() {
        defined(siamois, CODE);
        redirection().setBranchTopTerm(new Concept());

        assertThat(resolver.findDefaultVocabulary(INSTITUTION, PROJECT, CODE)).contains(siamois);
    }

    @Test
    void findDefaultVocabulary_isEmpty_whenNoDefaultSourceDefinesTheCode() {
        assertThat(resolver.findDefaultVocabulary(INSTITUTION, PROJECT, "UNKNOWN")).isEmpty();
    }
}
