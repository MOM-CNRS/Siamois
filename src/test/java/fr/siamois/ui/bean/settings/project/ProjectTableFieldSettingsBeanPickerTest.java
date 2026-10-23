package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import org.mockito.ArgumentCaptor;
import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.form.layout.FormLayoutService;
import fr.siamois.domain.services.identifier.IdentifierResolverRegistry;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptCollectionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.ui.bean.LangBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The instance / thesaurus pickers of a field's vocabulary, the opening on a type and the list of saved values. */
class ProjectTableFieldSettingsBeanPickerTest {

    private static final String SERVER = "https://thesaurus.example.org";
    private static final String LANG = "fr";
    private static final String TYPE_NAME = "Creusement";

    private final VocabularyService vocabularyService = mock(VocabularyService.class);
    private final TableFieldConfigService tableService = mock(TableFieldConfigService.class);
    private final FormConfigService formConfigService = mock(FormConfigService.class);
    private final ConceptService conceptService = mock(ConceptService.class);
    private final LabelService labelService = mock(LabelService.class);
    private final VocabularyMapper vocabularyMapper = mock(VocabularyMapper.class);
    private final FieldConfigurationService fieldConfigurationService = mock(FieldConfigurationService.class);

    private ProjectTableFieldSettingsBean bean;

    @BeforeEach
    void setUp() {
        LangBean langBean = mock(LangBean.class);
        when(langBean.getLanguageCode()).thenReturn(LANG);
        when(langBean.msg(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(langBean.msg(anyString(), any(Object[].class))).thenAnswer(invocation -> invocation.getArgument(0));
        bean = new ProjectTableFieldSettingsBean(tableService, formConfigService, conceptService,
                mock(ConceptCollectionService.class), vocabularyService, vocabularyMapper, labelService, langBean,
                new IdentifierResolverRegistry(), mock(FormLayoutService.class), fieldConfigurationService);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        bean.setProject(project);
    }

    private static ThesaurusInfo thesaurus(String id, String label) {
        return new ThesaurusInfo(SERVER, id, label, LANG);
    }

    private static Vocabulary vocabularyAt(String baseUri) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setBaseUri(baseUri);
        return vocabulary;
    }

    // ---- instances -----------------------------------------------------------------------------------

    @Test
    void getDraftServerOptions_shouldOfferTheDefaultAndTheKnownInstances_withoutTrailingSlash() {
        when(vocabularyService.findAll()).thenReturn(List.of(vocabularyAt("https://a.example.org/"), vocabularyAt("https://a.example.org")));

        assertThat(bean.getDraftServerOptions())
                .containsExactly("https://thesaurus.mom.fr", "https://a.example.org");
    }

    @Test
    void completeDraftServers_shouldFilterWhatWasTyped_andOfferEverythingWhenEmpty() {
        when(vocabularyService.findAll()).thenReturn(List.of(vocabularyAt("https://a.example.org")));

        assertThat(bean.completeDraftServers("EXAMPLE")).containsExactly("https://a.example.org");
        assertThat(bean.completeDraftServers("")).hasSize(2);
        assertThat(bean.completeDraftServers(null)).hasSize(2);
    }

    @Test
    void onDraftServerChange_shouldListTheThesauriOfTheInstance_andForgetTheOldOnes() throws Exception {
        bean.setDraftServerUrl(SERVER);
        when(vocabularyService.findAllThesaurusOf(SERVER, LANG)).thenReturn(List.of(thesaurus("th1", "Un")));
        bean.setDraftThesaurus(thesaurus("old", "Old"));
        bean.setDraftConnectionTested(true);

        bean.onDraftServerChange();

        assertThat(bean.getDraftThesaurusOptions()).containsExactly(thesaurus("th1", "Un"));
        assertThat(bean.getDraftThesaurus()).isNull();
        assertThat(bean.isDraftConnectionTested()).isFalse();
        assertThat(bean.getDrawerErrorMessage()).isNull();
    }

    @Test
    void onDraftServerChange_shouldTellTheUser_whenTheInstanceCannotBeReached() throws Exception {
        bean.setDraftServerUrl(SERVER);
        when(vocabularyService.findAllThesaurusOf(SERVER, LANG)).thenThrow(new InvalidEndpointException("down"));

        bean.onDraftServerChange();

        assertThat(bean.getDraftThesaurusOptions()).isEmpty();
        assertThat(bean.getDrawerErrorMessage()).isEqualTo("projectTables.drawer.params.serverUnreachable");
    }

    @Test
    void addDraftServer_shouldIgnoreABlankAddress() throws Exception {
        bean.setNewServerUrl("  ");

        bean.addDraftServer();

        verify(vocabularyService, never()).findAllThesaurusOf(anyString(), anyString());
        assertThat(bean.getDraftServerUrl()).isNull();
    }

    @Test
    void addDraftServer_shouldRefuseAnInstanceThatDoesNotAnswer() throws Exception {
        bean.setNewServerUrl(SERVER + "/");
        when(vocabularyService.findAllThesaurusOf(SERVER, LANG)).thenThrow(new InvalidEndpointException("down"));

        bean.addDraftServer();

        assertThat(bean.getDrawerErrorMessage()).isEqualTo("projectTables.drawer.params.serverUnreachable");
        assertThat(bean.getDraftServerUrl()).isNull();
    }

    @Test
    void addDraftServer_shouldAddAndSelectAnInstanceThatAnswers() throws Exception {
        bean.setNewServerUrl(SERVER + "/");
        when(vocabularyService.findAll()).thenReturn(List.of());
        when(vocabularyService.findAllThesaurusOf(SERVER, LANG)).thenReturn(List.of(thesaurus("th1", "Un")));

        bean.addDraftServer();

        assertThat(bean.getDraftServerUrl()).isEqualTo(SERVER);
        assertThat(bean.getDraftServerOptions()).contains(SERVER);
        assertThat(bean.getNewServerUrl()).isNull();
        assertThat(bean.getDraftThesaurusOptions()).hasSize(1);
    }

    // ---- thesauri -------------------------------------------------------------------------------------

    @Test
    void completeDraftThesauri_shouldMatchTheLabelOrTheIdentifier() throws Exception {
        bean.setDraftServerUrl(SERVER);
        when(vocabularyService.findAllThesaurusOf(SERVER, LANG))
                .thenReturn(List.of(thesaurus("th1", "Vocabulaire"), thesaurus("th2", "Autre")));
        bean.onDraftServerChange();

        assertThat(bean.completeDraftThesauri("vocab")).extracting(ThesaurusInfo::idTheso).containsExactly("th1");
        assertThat(bean.completeDraftThesauri("TH2")).extracting(ThesaurusInfo::idTheso).containsExactly("th2");
        assertThat(bean.completeDraftThesauri(null)).hasSize(2);
    }

    @Test
    void onDraftThesaurusChange_shouldLoadTheThesaurusPicked() throws Exception {
        ThesaurusInfo picked = thesaurus("th1", "Un");
        bean.setDraftThesaurus(picked);
        Vocabulary vocabulary = vocabularyAt(SERVER);
        VocabularyDTO dto = new VocabularyDTO();
        dto.setBaseUri(SERVER);
        dto.setExternalVocabularyId("th1");
        when(vocabularyService.resolveRedirections(picked.completeThesaurusUrl())).thenReturn(picked.completeThesaurusUrl());
        when(vocabularyService.findOrCreateVocabularyOfUri(picked.completeThesaurusUrl())).thenReturn(vocabulary);
        when(vocabularyMapper.convert(vocabulary)).thenReturn(dto);

        bean.onDraftThesaurusChange();

        assertThat(bean.isDraftConnectionTested()).isTrue();
        assertThat(bean.getDraftVocabulary()).isSameAs(dto);
    }

    @Test
    void onDraftThesaurusChange_shouldLoadNothing_whenTheFieldWasCleared() {
        bean.setDraftThesaurus(null);
        bean.setDraftConnectionTested(true);

        bean.onDraftThesaurusChange();

        assertThat(bean.isDraftConnectionTested()).isFalse();
        assertThat(bean.getDraftThesaurusUrl()).isNull();
        verify(vocabularyMapper, never()).convert(any(Vocabulary.class));
    }

    // ---- a source named by a field code ----------------------------------------------------------------

    private VocabularyDTO loadedThesaurus(String id) throws Exception {
        Vocabulary vocabulary = vocabularyAt(SERVER);
        vocabulary.setId(7L);
        VocabularyDTO dto = new VocabularyDTO();
        dto.setId(7L);
        dto.setBaseUri(SERVER);
        dto.setExternalVocabularyId(id);
        String url = SERVER + "/?idt=" + id;
        when(vocabularyService.resolveRedirections(url)).thenReturn(url);
        when(vocabularyService.findOrCreateVocabularyOfUri(url)).thenReturn(vocabulary);
        when(vocabularyService.findVocabularyById(7L)).thenReturn(vocabulary);
        when(vocabularyMapper.convert(vocabulary)).thenReturn(dto);
        return dto;
    }

    @Test
    void selectDraftSource_code_shouldListTheFieldCodesOfTheThesaurusPicked() throws Exception {
        loadedThesaurus("th1");
        bean.setDraftThesaurusUrl(SERVER + "/?idt=th1");
        bean.selectDraftSource(ProjectTableFieldSettingsBean.FIELD_CODE);
        when(fieldConfigurationService.findFieldCodesOf(any(Vocabulary.class))).thenReturn(List.of("SIAAU.TYPE", "OWN.CODE"));

        bean.testThesaurusConnection();

        assertThat(bean.getDraftSource()).isEqualTo(ProjectTableFieldSettingsBean.FIELD_CODE);
        assertThat(bean.getDraftCodeOptions()).containsExactly("SIAAU.TYPE", "OWN.CODE");
    }

    @Test
    void selectDraftSource_code_shouldStartFromTheThesaurusTheProjectDocumentsItsFieldsIn() throws Exception {
        loadedThesaurus("th1");
        when(fieldConfigurationService.findVocabularyUrlOfActionUnitId(42L)).thenReturn(Optional.of(SERVER + "/?idt=th1"));
        when(fieldConfigurationService.findFieldCodesOf(any(Vocabulary.class))).thenReturn(List.of("OWN.CODE"));
        when(vocabularyService.findAll()).thenReturn(List.of());
        when(vocabularyService.findAllThesaurusOf(SERVER, LANG)).thenReturn(List.of(thesaurus("th1", "Un")));

        bean.selectDraftSource(ProjectTableFieldSettingsBean.FIELD_CODE);

        assertThat(bean.isDraftConnectionTested()).isTrue();
        assertThat(bean.getDraftSource()).isEqualTo(ProjectTableFieldSettingsBean.FIELD_CODE);
        assertThat(bean.getDraftCodeOptions()).containsExactly("OWN.CODE");
    }

    @Test
    void selectDraftSource_code_startsFromTheFieldsOwnCodeInTheDefaultThesaurus() throws Exception {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(12L);
        bean.getProject().setCreatedByInstitution(institution);
        bean.setDraftFieldCode("SIARU.GEOMORPHO");
        Vocabulary siamois = vocabularyAt(SERVER);
        siamois.setId(7L);
        VocabularyDTO dto = new VocabularyDTO();
        dto.setId(7L);
        dto.setBaseUri(SERVER);
        dto.setExternalVocabularyId("th1");
        when(fieldConfigurationService.findDefaultVocabularyOfFieldCode(12L, 42L, "SIARU.GEOMORPHO")).thenReturn(Optional.of(siamois));
        when(vocabularyMapper.convert(siamois)).thenReturn(dto);
        when(vocabularyService.findVocabularyById(7L)).thenReturn(siamois);
        when(vocabularyService.findAll()).thenReturn(List.of());
        when(vocabularyService.findAllThesaurusOf(SERVER, LANG)).thenReturn(List.of(thesaurus("th1", "SIAMOIS")));
        when(fieldConfigurationService.findFieldCodesOf(siamois)).thenReturn(List.of("SIARU.GEOMORPHO", "OTHER"));

        bean.selectDraftSource(ProjectTableFieldSettingsBean.FIELD_CODE);

        assertThat(bean.getDraftSource()).isEqualTo(ProjectTableFieldSettingsBean.FIELD_CODE);
        assertThat(bean.isDraftConnectionTested()).isTrue();
        assertThat(bean.getDraftCodeChoice()).isEqualTo("SIARU.GEOMORPHO");
        assertThat(bean.getDraftCodeOptions()).containsExactly("SIARU.GEOMORPHO", "OTHER");
        assertThat(bean.getDraftThesaurus()).isEqualTo(thesaurus("th1", "SIAMOIS"));
    }

    @Test
    void selectDraftSource_code_shouldTellTheUser_whenTheThesaurusDefinesNoCode() throws Exception {
        loadedThesaurus("th1");
        bean.setDraftThesaurusUrl(SERVER + "/?idt=th1");
        bean.selectDraftSource(ProjectTableFieldSettingsBean.FIELD_CODE);
        when(fieldConfigurationService.findFieldCodesOf(any(Vocabulary.class))).thenReturn(List.of());

        bean.testThesaurusConnection();

        assertThat(bean.getDraftCodeOptions()).isEmpty();
        assertThat(bean.getDrawerErrorMessage()).isEqualTo("projectTables.drawer.params.codeNone");
    }

    @Test
    void selectDraftSource_code_shouldTellTheUser_whenTheThesaurusIsNotASiamoisOne() throws Exception {
        loadedThesaurus("th1");
        bean.setDraftThesaurusUrl(SERVER + "/?idt=th1");
        bean.selectDraftSource(ProjectTableFieldSettingsBean.FIELD_CODE);
        when(fieldConfigurationService.findFieldCodesOf(any(Vocabulary.class)))
                .thenThrow(new fr.siamois.domain.models.exceptions.api.NotSiamoisThesaurusException("no fields branch"));

        bean.testThesaurusConnection();

        assertThat(bean.getDrawerErrorMessage()).isEqualTo("myProfile.thesaurus.siamois.invalid");
    }

    // ---- opening on a type ----------------------------------------------------------------------------

    private void typesOfUe(String... names) {
        when(tableService.listTypes(42L, ConfigurableTable.UE))
                .thenReturn(java.util.Arrays.stream(names).map(TypeSummary::new).toList());
    }

    private void conceptLabelled(long conceptId, String label) {
        Concept concept = new Concept();
        concept.setId(conceptId);
        ConceptLabel conceptLabel = mock(ConceptLabel.class);
        when(conceptLabel.getLabel()).thenReturn(label);
        when(conceptService.findById(conceptId)).thenReturn(Optional.of(concept));
        when(labelService.findLabelOf(concept, LANG)).thenReturn(conceptLabel);
    }

    @Test
    void openOn_shouldStayOnTheTable_whenTheConceptIsUnknown() {
        typesOfUe(TYPE_NAME);
        when(conceptService.findById(12L)).thenReturn(Optional.empty());

        bean.openOn(ConfigurableTable.UE, 12L, null);

        assertThat(bean.getSelectedTable()).isEqualTo(ConfigurableTable.UE);
        assertThat(bean.getSelectedTypeName()).isNull();
    }

    @Test
    void openOn_shouldStayOnTheTable_whenNoTypeCarriesTheConceptLabel() {
        typesOfUe(TYPE_NAME);
        conceptLabelled(12L, "Autre");

        bean.openOn(ConfigurableTable.UE, 12L, null);

        assertThat(bean.getSelectedTypeName()).isNull();
    }

    @Test
    void openOn_shouldSelectTheTypeAndUnfoldTheTree() {
        typesOfUe(TYPE_NAME);
        conceptLabelled(12L, "creusement");

        bean.openOn(ConfigurableTable.UE, 12L, null);

        assertThat(bean.getSelectedTypeName()).isEqualTo(TYPE_NAME);
        assertThat(bean.isTableExpanded(ConfigurableTable.UE)).isTrue();
        assertThat(bean.isTypeExpanded(ConfigurableTable.UE, TYPE_NAME)).isTrue();
        assertThat(bean.isDrawerOpen()).isFalse();
    }

    @Test
    void openOn_shouldOpenTheFieldAsked() {
        typesOfUe(TYPE_NAME);
        conceptLabelled(12L, TYPE_NAME);
        TypeFieldFormConfig other = TypeFieldFormConfig.builder().id(1L).name("autre").build();
        TypeFieldFormConfig wanted = TypeFieldFormConfig.builder().id(34L).name("voulu")
                .type(fr.siamois.domain.models.settings.tableconfig.FieldType.TEXT).build();
        when(tableService.getFieldsConfig(42L, ConfigurableTable.UE, TYPE_NAME))
                .thenReturn(new TypeFieldsConfig(List.of(other, wanted)));

        bean.openOn(ConfigurableTable.UE, 12L, 34L);

        assertThat(bean.isDrawerOpen()).isTrue();
        assertThat(bean.getDraftOriginalName()).isEqualTo("voulu");
    }

    @Test
    void openOn_shouldLeaveTheDrawerClosed_whenTheFieldIsNotOfTheType() {
        typesOfUe(TYPE_NAME);
        conceptLabelled(12L, TYPE_NAME);
        when(tableService.getFieldsConfig(42L, ConfigurableTable.UE, TYPE_NAME))
                .thenReturn(new TypeFieldsConfig(List.of(TypeFieldFormConfig.builder().id(1L).name("autre").build())));

        bean.openOn(ConfigurableTable.UE, 12L, 99L);

        assertThat(bean.isDrawerOpen()).isFalse();
    }

    // ---- values the saved configuration offers ----------------------------------------------------------

    private void openFieldWithASavedConfig(FieldConfigurationService.ValuesPage page) {
        bean.setSelectedTable(ConfigurableTable.UE);
        bean.setSelectedTypeName(TYPE_NAME);
        bean.setDraftOriginalName("etat");
        FormConfig formConfig = new FormConfig();
        CustomFieldConcept field = mock(CustomFieldConcept.class);
        ConceptFieldFormConfig config = mock(ConceptFieldFormConfig.class);
        when(tableService.findFormConfig(42L, ConfigurableTable.UE, TYPE_NAME)).thenReturn(Optional.of(formConfig));
        when(tableService.findField(42L, ConfigurableTable.UE, TYPE_NAME, "etat")).thenReturn(Optional.of(field));
        when(formConfigService.findConceptConfigFor(formConfig, field)).thenReturn(Optional.of(config));
        when(fieldConfigurationService.fetchValuesPage(eq(field), eq(config), eq(LANG), any(), anyInt(), anyInt()))
                .thenReturn(page);
    }

    @Test
    void searchValues_shouldListTheFirstPageWithItsCount() {
        openFieldWithASavedConfig(new FieldConfigurationService.ValuesPage(List.of(), 137, false, true));

        bean.setValuesQuery("sol");
        bean.searchValues();

        assertThat(bean.isValuesAvailable()).isTrue();
        assertThat(bean.getValuesTotal()).isEqualTo(137);
        assertThat(bean.isValuesTotalCapped()).isFalse();
        assertThat(bean.isValuesHasNext()).isTrue();
        assertThat(bean.isValuesHasPrevious()).isFalse();
        assertThat(bean.getValuesQuery()).isEqualTo("sol");
        verify(fieldConfigurationService).fetchValuesPage(any(), any(), eq(LANG), eq("sol"), eq(0),
                eq(ProjectTableFieldSettingsBean.VALUES_PAGE_SIZE));
    }

    @Test
    void nextAndPreviousValuesPage_shouldMoveByAPage_neverBelowTheFirst() {
        openFieldWithASavedConfig(new FieldConfigurationService.ValuesPage(List.of(), 100, true, true));
        int size = ProjectTableFieldSettingsBean.VALUES_PAGE_SIZE;

        bean.nextValuesPage();
        assertThat(bean.getValuesOffset()).isEqualTo(size);
        assertThat(bean.isValuesHasPrevious()).isTrue();
        assertThat(bean.getValuesFirstRank()).isEqualTo(size + 1);

        bean.previousValuesPage();
        bean.previousValuesPage();
        assertThat(bean.getValuesOffset()).isZero();
        assertThat(bean.getValuesLastRank()).isZero();
    }

    @Test
    void searchValues_shouldListNothing_whenNoConfigurationIsSaved() {
        bean.setSelectedTable(ConfigurableTable.UE);
        bean.setSelectedTypeName(TYPE_NAME);
        bean.setDraftOriginalName("etat");

        bean.searchValues();

        assertThat(bean.isValuesAvailable()).isFalse();
        assertThat(bean.getValuesItems()).isEmpty();
    }

    @Test
    void searchValues_shouldReadTheDefaultCode_whenNothingIsSavedForTheField() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(12L);
        bean.getProject().setCreatedByInstitution(institution);
        bean.setSelectedTable(ConfigurableTable.UE);
        bean.setSelectedTypeName(TYPE_NAME);
        bean.setDraftOriginalName("etat");
        bean.setDraftFieldCode("SIARU.GEOMORPHO");
        Vocabulary siamois = vocabularyAt(SERVER);
        FormConfig formConfig = new FormConfig();
        CustomFieldConcept field = mock(CustomFieldConcept.class);
        when(tableService.findFormConfig(42L, ConfigurableTable.UE, TYPE_NAME)).thenReturn(Optional.of(formConfig));
        when(tableService.findField(42L, ConfigurableTable.UE, TYPE_NAME, "etat")).thenReturn(Optional.of(field));
        when(formConfigService.findConceptConfigFor(formConfig, field)).thenReturn(Optional.empty());
        when(fieldConfigurationService.findDefaultVocabularyOfFieldCode(12L, 42L, "SIARU.GEOMORPHO")).thenReturn(Optional.of(siamois));
        when(fieldConfigurationService.fetchValuesPage(eq(field), any(ConceptFieldFormConfig.class), eq(LANG), any(), anyInt(), anyInt()))
                .thenReturn(new FieldConfigurationService.ValuesPage(List.of(), 12, false, false));

        bean.searchValues();

        assertThat(bean.isValuesAvailable()).isTrue();
        assertThat(bean.getValuesTotal()).isEqualTo(12);
        ArgumentCaptor<ConceptFieldFormConfig> read = ArgumentCaptor.forClass(ConceptFieldFormConfig.class);
        verify(fieldConfigurationService).fetchValuesPage(eq(field), read.capture(), eq(LANG), any(), anyInt(), anyInt());
        assertThat(read.getValue().getVocabulary()).isSameAs(siamois);
        assertThat(read.getValue().getFieldCode()).isEqualTo("SIARU.GEOMORPHO");
    }

    @Test
    void searchValues_shouldListNothing_whenTheReadFails() {
        openFieldWithASavedConfig(new FieldConfigurationService.ValuesPage(List.of(), 1, false, false));
        when(fieldConfigurationService.fetchValuesPage(any(), any(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("boom"));

        bean.searchValues();

        assertThat(bean.isValuesAvailable()).isFalse();
    }
}
