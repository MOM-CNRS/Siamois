package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.misc.ProgressWrapper;
import fr.siamois.domain.models.settings.ProjectCodeSource;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.vocabulary.*;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptAutocompleteDetachedDTO;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.ui.bean.LangBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The source of the types of a table, set on the table's page. */
class ProjectTableTypeSourceBeanTest {

    private static final ConfigurableTable TABLE = ConfigurableTable.UE;
    private static final String TYPE_CODE = TABLE.getFieldCode();
    private static final String URL = "https://thesaurus.example.org/?idt=th1";

    private final CodeSourceService codeSourceService = mock(CodeSourceService.class);
    private final FieldConfigurationService fieldConfigurationService = mock(FieldConfigurationService.class);
    private final VocabularyService vocabularyService = mock(VocabularyService.class);
    private final VocabularyMapper vocabularyMapper = mock(VocabularyMapper.class);
    private final ConceptService conceptService = mock(ConceptService.class);
    private final LabelService labelService = mock(LabelService.class);

    private ProjectTableTypeSourceBean bean;
    private ActionUnitDTO project;
    private Vocabulary vocabulary;
    private VocabularyDTO vocabularyDTO;

    @BeforeEach
    void setUp() {
        LangBean langBean = mock(LangBean.class);
        when(langBean.getLanguageCode()).thenReturn("fr");
        when(langBean.msg(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        bean = new ProjectTableTypeSourceBean(codeSourceService, fieldConfigurationService, vocabularyService,
                vocabularyMapper, conceptService, labelService, langBean);
        project = new ActionUnitDTO();
        project.setId(42L);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(12L);
        project.setCreatedByInstitution(institution);

        vocabulary = new Vocabulary();
        vocabulary.setId(7L);
        vocabularyDTO = new VocabularyDTO();
        vocabularyDTO.setId(7L);
        vocabularyDTO.setBaseUri("https://thesaurus.example.org");
        vocabularyDTO.setExternalVocabularyId("th1");
        when(vocabularyMapper.convert(vocabulary)).thenReturn(vocabularyDTO);
        when(vocabularyService.findVocabularyById(7L)).thenReturn(vocabulary);
    }

    private void thesaurusLoadable() throws Exception {
        when(vocabularyService.resolveRedirections(URL)).thenReturn(URL);
        when(vocabularyService.findOrCreateVocabularyOfUri(URL)).thenReturn(vocabulary);
    }

    @Test
    void open_showsTheDefaultSource_whenTheProjectRedirectsNothing() {
        when(codeSourceService.find(42L, TYPE_CODE)).thenReturn(Optional.empty());

        bean.open(project, TABLE);

        assertThat(bean.isAvailable()).isTrue();
        assertThat(bean.getSource()).isNull();
        assertThat(bean.isCustom()).isFalse();
    }

    @Test
    void open_showsWhereTheTypeCodeIsReadByDefault_toBeConsulted() {
        when(codeSourceService.find(42L, TYPE_CODE)).thenReturn(Optional.empty());
        when(fieldConfigurationService.findDefaultVocabularyOfFieldCode(12L, 42L, TYPE_CODE)).thenReturn(Optional.of(vocabulary));
        vocabulary.setExternalVocabularyId("th1");
        fr.siamois.domain.models.vocabulary.label.VocabularyLabel label = mock(fr.siamois.domain.models.vocabulary.label.VocabularyLabel.class);
        when(label.getValue()).thenReturn("Thésaurus SIAMOIS");
        when(labelService.findLabelOf(vocabulary, "fr")).thenReturn(label);

        bean.open(project, TABLE);

        assertThat(bean.getDefaultCode()).isEqualTo(TYPE_CODE);
        assertThat(bean.getDefaultThesaurusLabel()).isEqualTo("Thésaurus SIAMOIS");
        assertThat(bean.getDefaultServerUrl()).isEqualTo("https://thesaurus.example.org");
        assertThat(bean.getDefaultThesaurusUrl()).isEqualTo(vocabularyDTO.completeUri());
        assertThat(bean.getSource()).isNull();
    }

    @Test
    void onServerChange_listsTheThesauriOfTheInstance_andForgetsTheOldOnes() throws Exception {
        bean.open(project, TABLE);
        bean.setServerUrl("https://thesaurus.example.org");
        bean.setThesaurus(new fr.siamois.domain.models.vocabulary.ThesaurusInfo("https://thesaurus.example.org", "old", "Old", "fr"));
        bean.setConnectionTested(true);
        fr.siamois.domain.models.vocabulary.ThesaurusInfo th1 =
                new fr.siamois.domain.models.vocabulary.ThesaurusInfo("https://thesaurus.example.org", "th1", "Un", "fr");
        when(vocabularyService.findAllThesaurusOf("https://thesaurus.example.org", "fr")).thenReturn(List.of(th1));

        bean.onServerChange();

        assertThat(bean.getThesaurusOptions()).containsExactly(th1);
        assertThat(bean.getThesaurus()).isNull();
        assertThat(bean.isConnectionTested()).isFalse();
    }

    @Test
    void onThesaurusChange_loadsTheThesaurusPickedAndListsItsCodes() throws Exception {
        fr.siamois.domain.models.vocabulary.ThesaurusInfo th1 =
                new fr.siamois.domain.models.vocabulary.ThesaurusInfo("https://thesaurus.example.org", "th1", "Un", "fr");
        bean.open(project, TABLE);
        bean.setSource(ProjectTableTypeSourceBean.FIELD_CODE);
        bean.setThesaurus(th1);
        when(vocabularyService.resolveRedirections(th1.completeThesaurusUrl())).thenReturn(th1.completeThesaurusUrl());
        when(vocabularyService.findOrCreateVocabularyOfUri(th1.completeThesaurusUrl())).thenReturn(vocabulary);
        when(vocabularyService.findAllThesaurusOf("https://thesaurus.example.org", "fr")).thenReturn(List.of(th1));
        when(fieldConfigurationService.findFieldCodesOf(vocabulary)).thenReturn(List.of("OWN.TYPE"));

        bean.onThesaurusChange();

        assertThat(bean.isConnectionTested()).isTrue();
        assertThat(bean.getCodeOptions()).containsExactly("OWN.TYPE");
    }

    @Test
    void completeServers_offersTheDefaultAndTheKnownInstances() {
        Vocabulary known = new Vocabulary();
        known.setBaseUri("https://a.example.org/");
        when(vocabularyService.findAll()).thenReturn(List.of(known));

        assertThat(bean.getServerOptions()).containsExactly("https://thesaurus.mom.fr", "https://a.example.org");
        assertThat(bean.completeServers("EXAMPLE")).containsExactly("https://a.example.org");
    }

    @Test
    void open_showsTheFieldCodeTheProjectReadsTheTypesFrom() throws Exception {
        ProjectCodeSource current = new ProjectCodeSource();
        current.setVocabulary(vocabulary);
        current.setSourceFieldCode("OWN.TYPE");
        when(codeSourceService.find(42L, TYPE_CODE)).thenReturn(Optional.of(current));
        when(fieldConfigurationService.findFieldCodesOf(vocabulary)).thenReturn(List.of("OWN.TYPE", "OTHER"));

        bean.open(project, TABLE);

        assertThat(bean.getSource()).isEqualTo(ProjectTableTypeSourceBean.FIELD_CODE);
        assertThat(bean.isConnectionTested()).isTrue();
        assertThat(bean.getCodeOptions()).containsExactly("OWN.TYPE", "OTHER");
        assertThat(bean.getCodeChoice()).isEqualTo("OWN.TYPE");
    }

    @Test
    void open_showsTheBranchTheProjectReadsTheTypesFrom() {
        Concept top = new Concept();
        top.setExternalId("123");
        top.setVocabulary(vocabulary);
        ProjectCodeSource current = new ProjectCodeSource();
        current.setBranchTopTerm(top);
        when(codeSourceService.find(42L, TYPE_CODE)).thenReturn(Optional.of(current));
        ConceptLabel label = mock(ConceptLabel.class);
        when(label.getLabel()).thenReturn("Types");
        when(labelService.findLabelOf(top, "fr")).thenReturn(label);

        bean.open(project, TABLE);

        assertThat(bean.getSource()).isEqualTo(ProjectTableTypeSourceBean.BRANCH);
        assertThat(bean.isBranchSelected()).isTrue();
    }

    @Test
    void select_code_startsFromTheThesaurusTheProjectDocumentsItsTypesIn_andListsItsCodes() throws Exception {
        thesaurusLoadable();
        bean.open(project, TABLE);
        when(fieldConfigurationService.findVocabularyUrlOfActionUnitId(42L)).thenReturn(Optional.of(URL));
        when(fieldConfigurationService.findFieldCodesOf(vocabulary)).thenReturn(List.of("OWN.TYPE"));

        bean.select(ProjectTableTypeSourceBean.FIELD_CODE);

        assertThat(bean.getSource()).isEqualTo(ProjectTableTypeSourceBean.FIELD_CODE);
        assertThat(bean.isConnectionTested()).isTrue();
        assertThat(bean.getCodeOptions()).containsExactly("OWN.TYPE");
    }

    @Test
    void select_fallsBackOnTheInstitutionThesaurus_whenTheProjectHasNone() throws Exception {
        thesaurusLoadable();
        bean.open(project, TABLE);
        when(fieldConfigurationService.findVocabularyUrlOfActionUnitId(42L)).thenReturn(Optional.empty());
        when(fieldConfigurationService.findVocabularyUrlOfInstitutionId(12L)).thenReturn(Optional.of(URL));
        when(fieldConfigurationService.findFieldCodesOf(vocabulary)).thenReturn(List.of("OWN.TYPE"));

        bean.select(ProjectTableTypeSourceBean.FIELD_CODE);

        assertThat(bean.getThesaurusUrl()).isEqualTo(vocabularyDTO.completeUri());
    }

    @Test
    void select_default_forgetsTheDraft() {
        bean.open(project, TABLE);
        bean.setSource(ProjectTableTypeSourceBean.FIELD_CODE);
        bean.setCodeChoice("X");

        bean.select(null);

        assertThat(bean.getSource()).isNull();
        assertThat(bean.getCodeChoice()).isNull();
    }

    @Test
    void save_appliesTheFieldCodePicked() throws Exception {
        thesaurusLoadable();
        bean.open(project, TABLE);
        bean.setThesaurusUrl(URL);
        bean.select(ProjectTableTypeSourceBean.FIELD_CODE);
        bean.testConnection();
        bean.setCodeChoice("OWN.TYPE");

        assertThat(bean.save()).isTrue();

        verify(codeSourceService).useFieldCode(42L, TYPE_CODE, vocabulary, "OWN.TYPE");
        assertThat(bean.getSavedMessage()).isEqualTo("projectTables.typeSource.saved");
    }

    @Test
    void save_refusesAFieldCodeSourceWithNoCode() throws Exception {
        thesaurusLoadable();
        bean.open(project, TABLE);
        bean.setThesaurusUrl(URL);
        bean.select(ProjectTableTypeSourceBean.FIELD_CODE);
        bean.testConnection();

        assertThat(bean.save()).isFalse();

        assertThat(bean.getErrorMessage()).isEqualTo("projectTables.typeSource.codeMissing");
        verify(codeSourceService, never()).useFieldCode(anyLong(), anyString(), any(Vocabulary.class), anyString());
    }

    @Test
    void save_appliesTheBranchPicked() throws Exception {
        bean.open(project, TABLE);
        bean.setSource(ProjectTableTypeSourceBean.BRANCH);
        ConceptDTO concept = ConceptDTO.builder().externalId("123").vocabulary(vocabularyDTO).deleted(false).build();
        ConceptLabelDTO label = new ConceptPrefLabelDTO();
        label.setConcept(concept);
        bean.setBranchConcept(new ConceptAutocompleteDetachedDTO(label, "Types", List.of(), "", vocabularyDTO.completeUri()));

        assertThat(bean.save()).isTrue();

        verify(codeSourceService).useBranch(eq(42L), eq(TYPE_CODE), eq(concept), any(ProgressWrapper.class));
    }

    @Test
    void save_refusesABranchSourceWithNoBranch() {
        bean.open(project, TABLE);
        bean.setSource(ProjectTableTypeSourceBean.BRANCH);

        assertThat(bean.save()).isFalse();

        assertThat(bean.getErrorMessage()).isEqualTo("projectTables.typeSource.brancheMissing");
    }

    @Test
    void save_tellsTheUser_whenTheSourceCannotBeApplied() throws Exception {
        thesaurusLoadable();
        bean.open(project, TABLE);
        bean.setThesaurusUrl(URL);
        bean.select(ProjectTableTypeSourceBean.FIELD_CODE);
        bean.testConnection();
        bean.setCodeChoice("OWN.TYPE");
        doThrow(new IllegalStateException("down")).when(codeSourceService)
                .useFieldCode(anyLong(), anyString(), any(Vocabulary.class), anyString());

        assertThat(bean.save()).isFalse();

        assertThat(bean.getErrorMessage()).isEqualTo("projectTables.typeSource.saveError");
    }

    @Test
    void save_withTheDefaultSource_readsTheTypesWhereTheyAreReadByDefaultAgain() {
        when(codeSourceService.find(42L, TYPE_CODE)).thenReturn(Optional.of(new ProjectCodeSource()));
        bean.open(project, TABLE);
        bean.select(null);

        assertThat(bean.save()).isTrue();

        verify(codeSourceService).clear(42L, TYPE_CODE);
    }

    @Test
    void save_withTheDefaultSource_doesNothing_whenTheProjectRedirectsNothing() {
        when(codeSourceService.find(42L, TYPE_CODE)).thenReturn(Optional.empty());
        bean.open(project, TABLE);

        assertThat(bean.save()).isFalse();

        verify(codeSourceService, never()).clear(anyLong(), anyString());
    }

    @Test
    void useDefault_readsTheTypesWhereTheyAreReadByDefaultAgain() {
        bean.open(project, TABLE);

        assertThat(bean.useDefault()).isTrue();

        verify(codeSourceService).clear(42L, TYPE_CODE);
        assertThat(bean.getSource()).isNull();
    }

    @Test
    void open_keepsTheMessage_whenTheSameTableIsOpenedAgain() {
        bean.open(project, TABLE);
        bean.useDefault();

        bean.open(project, TABLE);

        assertThat(bean.getSavedMessage()).isEqualTo("projectTables.typeSource.reset");
    }
}
