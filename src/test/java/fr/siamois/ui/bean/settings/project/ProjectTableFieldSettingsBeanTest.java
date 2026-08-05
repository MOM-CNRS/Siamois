package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptCollectionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.dto.entity.vocabulary.ConceptPrefLabelDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptAutocompleteDetachedDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptCollectionDetachedDTO;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectTableFieldSettingsBeanTest {

    @InjectMocks
    private ProjectTableFieldSettingsBean bean;

    @Mock
    private TableFieldConfigService tableFieldConfigService;

    @Mock
    private FormConfigService formConfigService;

    @Mock
    private ConceptService conceptService;

    @Mock
    private ConceptCollectionService conceptCollectionService;

    @Mock
    private VocabularyService vocabularyService;

    @Mock
    private VocabularyMapper vocabularyMapper;

    @Mock
    private LangBean langBean;

    private ActionUnitDTO project;

    @BeforeEach
    void setUp() {
        project = new ActionUnitDTO();
        project.setId(42L);

        when(tableFieldConfigService.listTables()).thenReturn(
                List.of(ConfigurableTable.UE, ConfigurableTable.MOBILIER));
        when(tableFieldConfigService.listTypes(eq(42L), any())).thenReturn(
                List.of(new TypeSummary("_default", true), new TypeSummary("Céramique", false)));
        when(tableFieldConfigService.getFormConfig(eq(42L), any(), any())).thenReturn(
                TypeFormConfig.builder().typeName("Céramique").build());
        when(tableFieldConfigService.getFieldsConfig(eq(42L), any(), any(String.class))).thenReturn(
                new TypeFieldsConfig());
    }

    @Test
    void init_shouldSelectFirstTableAndFirstNonDefaultType() {
        bean.init(project);

        assertThat(bean.getSelectedTable()).isEqualTo(ConfigurableTable.UE);
        assertThat(bean.getSelectedTypeName()).isEqualTo("Céramique");
        assertThat(bean.getFormConfig()).isNotNull();
        assertThat(bean.getFieldsConfig()).isNotNull();
    }

    @Test
    void selectTable_shouldReloadTypesAndConfigsForNewTable() {
        bean.init(project);

        bean.selectTable(ConfigurableTable.MOBILIER);

        assertThat(bean.getSelectedTable()).isEqualTo(ConfigurableTable.MOBILIER);
        verify(tableFieldConfigService).listTypes(42L, ConfigurableTable.MOBILIER);
    }

    @Test
    void selectType_shouldReloadFormAndFieldsConfig() {
        bean.init(project);

        bean.selectType("_default");

        assertThat(bean.getSelectedTypeName()).isEqualTo("_default");
        verify(tableFieldConfigService).getFormConfig(42L, ConfigurableTable.UE, "_default");
        verify(tableFieldConfigService).getFieldsConfig(42L, ConfigurableTable.UE, "_default");
    }

    @Test
    void toggleFieldActive_shouldForwardTheFieldsAlreadyUpdatedValue() {
        bean.init(project);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder().name("Localisation").type(FieldType.TEXT).systemField(true).active(false).build();

        bean.toggleFieldActive(field);

        verify(tableFieldConfigService).setFieldActive(42L, ConfigurableTable.UE, "Céramique", "Localisation", false);
    }

    @Test
    void toggleFieldMandatory_shouldForwardTheFieldsAlreadyUpdatedValueForAdditionalField() {
        bean.init(project);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder().name("Remontage").type(FieldType.SELECT_ONE).systemField(false).mandatory(true).build();

        bean.toggleFieldMandatory(field);

        verify(tableFieldConfigService).setFieldMandatory(42L, ConfigurableTable.UE, "Céramique", "Remontage", true);
    }

    @Test
    void getSystemFields_and_getAdditionalFields_shouldPartitionByIsSystemField() {
        bean.init(project);
        TypeFieldFormConfig sys = TypeFieldFormConfig.builder().name("Identifiant").systemField(true).build();
        TypeFieldFormConfig custom = TypeFieldFormConfig.builder().name("Remontage").systemField(false).build();
        TypeFieldsConfig config = new TypeFieldsConfig();
        config.setFields(List.of(sys, custom));
        bean.setFieldsConfig(config);

        assertThat(bean.getSystemFields()).containsExactly(sys);
        assertThat(bean.getAdditionalFields()).containsExactly(custom);
    }

    /**
     * PrimeFaces reorders the list backing the draggable table itself while decoding the drag, so
     * the test moves the row the way the decode would and expects the listener to forward the names
     * as they then stand.
     */
    @Test
    void onAdditionalFieldReorder_shouldForwardTheAdditionalFieldNamesInTheirNewOrder() {
        bean.init(project);
        TypeFieldFormConfig sys = TypeFieldFormConfig.builder().name("Identifiant").systemField(true).build();
        TypeFieldFormConfig remontage = TypeFieldFormConfig.builder().name("Remontage").systemField(false).build();
        TypeFieldFormConfig couleur = TypeFieldFormConfig.builder().name("Couleur").systemField(false).build();
        TypeFieldsConfig config = new TypeFieldsConfig();
        config.setFields(List.of(sys, remontage, couleur));
        bean.setFieldsConfig(config);

        bean.getAdditionalFields().add(0, bean.getAdditionalFields().remove(1));
        bean.onAdditionalFieldReorder();

        verify(tableFieldConfigService).reorderAdditionalFields(42L, ConfigurableTable.UE, "Céramique",
                List.of("Couleur", "Remontage"));
    }

    @Test
    void deleteAdditionalField_shouldDelegateAndReload() {
        bean.init(project);

        bean.deleteAdditionalField("Remontage");

        verify(tableFieldConfigService).deleteAdditionalField(42L, ConfigurableTable.UE, "Céramique", "Remontage");
        verify(tableFieldConfigService, atLeastOnce()).getFieldsConfig(42L, ConfigurableTable.UE, "Céramique");
    }


    @Test
    void resolveFieldLabel_shouldTranslateSystemFieldsAndLeaveTheOthersAsIs() {
        bean.init(project);
        when(langBean.msg("recordingunit.property.type")).thenReturn("Type");

        TypeFieldFormConfig systemField = TypeFieldFormConfig.builder()
                .name("recordingunit.property.type").systemField(true).build();
        TypeFieldFormConfig additionalField = TypeFieldFormConfig.builder()
                .name("Nombre de tessons").systemField(false).build();

        assertThat(bean.resolveFieldLabel(systemField)).isEqualTo("Type");
        assertThat(bean.resolveFieldLabel(additionalField)).isEqualTo("Nombre de tessons");
        assertThat(systemField.getName()).isEqualTo("recordingunit.property.type");
    }

    @Test
    void openDrawerForEdit_shouldLockIdentityAndDefaultToPrincipalSourceForAConfigurableSystemField() {
        bean.init(project);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder()
                .name("Catégorie").type(FieldType.SELECT_ONE).systemField(true).sourceLabel("CATEGORIE").build();

        bean.openDrawerForEdit(field);

        assertThat(bean.isDrawerOpen()).isTrue();
        assertThat(bean.isDraftIsSystem()).isTrue();
        assertThat(bean.getDraftFieldCode()).isEqualTo("CATEGORIE");
        assertThat(bean.getDraftSource()).isEqualTo("principal");
        assertThat(bean.isDraftConfigurable()).isTrue();
    }

    @Test
    void openDrawerForEdit_shouldLeaveSourceUnsetForAConfigurableAdditionalField() {
        bean.init(project);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder()
                .name("Technique de fabrication").type(FieldType.SELECT_ONE).systemField(false).build();

        bean.openDrawerForEdit(field);

        assertThat(bean.isDraftIsSystem()).isFalse();
        assertThat(bean.getDraftSource()).isNull();
    }

    @Test
    void openDrawerForCreate_shouldResetSystemAndParamsState() {
        bean.init(project);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder()
                .name("Catégorie").type(FieldType.SELECT_ONE).systemField(true).sourceLabel("CATEGORIE").build();
        bean.openDrawerForEdit(field);

        bean.openDrawerForCreate();

        assertThat(bean.isDraftIsSystem()).isFalse();
        assertThat(bean.getDraftSource()).isNull();
        assertThat(bean.isDraftConfigurable()).isFalse();
    }

    @Test
    void selectDraftSource_shouldResetUrlConnectionAndAutocompleteState() {
        bean.init(project);
        bean.selectDraftSource("branche");
        bean.setDraftThesaurusUrl("https://example.org/thesaurus");
        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.testThesaurusConnection();
        }
        bean.setDraftBrancheConcept(conceptResult("Céramique"));

        bean.selectDraftSource("collection");

        assertThat(bean.getDraftSource()).isEqualTo("collection");
        assertThat(bean.getDraftThesaurusUrl()).isNull();
        assertThat(bean.isDraftConnectionTested()).isFalse();
        assertThat(bean.getDraftBrancheConcept()).isNull();
    }

    @Test
    void testThesaurusConnection_shouldSetTestedOnlyWhenUrlIsNotBlank() throws InvalidEndpointException {
        bean.init(project);
        bean.selectDraftSource("branche");

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.testThesaurusConnection();
            assertThat(bean.isDraftConnectionTested()).isFalse();
            messageUtilsMock.verify(() ->
                    MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "projectTables.drawer.params.connectionMissingUrl"));

            bean.setDraftThesaurusUrl("https://example.org/thesaurus");
            Vocabulary vocabulary = new Vocabulary();
            VocabularyDTO vocabularyDTO = VocabularyDTO.builder().build();
            when(vocabularyService.findOrCreateVocabularyOfUri("https://example.org/thesaurus")).thenReturn(vocabulary);
            when(vocabularyMapper.convert(vocabulary)).thenReturn(vocabularyDTO);

            bean.testThesaurusConnection();
            assertThat(bean.isDraftConnectionTested()).isTrue();
            messageUtilsMock.verify(() ->
                    MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "projectTables.drawer.params.connectionOk"));
        }
    }

    @Test
    void testThesaurusConnection_shouldReportAnErrorAndLeaveConnectionUntestedWhenTheUriIsInvalid() throws InvalidEndpointException {
        bean.init(project);
        bean.selectDraftSource("branche");
        bean.setDraftThesaurusUrl("not-a-uri");
        when(vocabularyService.findOrCreateVocabularyOfUri("not-a-uri")).thenThrow(new InvalidEndpointException("bad uri"));

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.testThesaurusConnection();

            assertThat(bean.isDraftConnectionTested()).isFalse();
            messageUtilsMock.verify(() ->
                    MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_ERROR, "myProfile.thesaurus.uri.invalid"));
        }
    }

    @Test
    void completeBrancheConcepts_and_completeCollections_shouldBeEmptyUntilTheConnectionIsTested() {
        bean.init(project);
        bean.selectDraftSource("branche");

        assertThat(bean.completeBrancheConcepts("ramiq")).isEmpty();
        assertThat(bean.completeCollections("numismatique")).isEmpty();
        verifyNoInteractions(conceptService, conceptCollectionService);
    }

    @Test
    void completeBrancheConcepts_and_completeCollections_shouldDelegateToTheRealServicesOnceConnected() throws InvalidEndpointException {
        bean.init(project);
        bean.selectDraftSource("branche");
        bean.setDraftThesaurusUrl("https://example.org/thesaurus");
        Vocabulary vocabulary = new Vocabulary();
        VocabularyDTO vocabularyDTO = VocabularyDTO.builder().build();
        when(vocabularyService.findOrCreateVocabularyOfUri("https://example.org/thesaurus")).thenReturn(vocabulary);
        when(vocabularyMapper.convert(vocabulary)).thenReturn(vocabularyDTO);
        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.testThesaurusConnection();
        }

        ConceptAutocompleteDetachedDTO ceramique = conceptResult("Céramique");
        when(conceptService.fetchAutocompleteFromRemoteThesaurus(vocabularyDTO, "ramiq")).thenReturn(List.of(ceramique));
        ConceptCollectionDetachedDTO numismatique = collectionResult("Collection numismatique");
        when(conceptCollectionService.fetchCollectionsFromRemoteThesaurus(vocabularyDTO)).thenReturn(List.of(numismatique));

        assertThat(bean.completeBrancheConcepts("ramiq")).containsExactly(ceramique);
        assertThat(bean.completeCollections("numismatique")).containsExactly("Collection numismatique");
    }

    private static ConceptAutocompleteDetachedDTO conceptResult(String label) {
        ConceptLabelDTO labelDTO = new ConceptPrefLabelDTO();
        labelDTO.setLabel(label);
        labelDTO.setConcept(ConceptDTO.builder().externalId("c1").vocabulary(VocabularyDTO.builder().build()).build());
        return new ConceptAutocompleteDetachedDTO(labelDTO, label, List.of(), "", "https://example.org/thesaurus");
    }

    private static ConceptCollectionDetachedDTO collectionResult(String label) {
        return new ConceptCollectionDetachedDTO("col1", VocabularyDTO.builder().build(), label, List.of(new LabelDTO("fr", label)));
    }

    @Test
    void saveDrawer_shouldPersistTheSelectedBranchConceptViaFormConfigService() throws InvalidEndpointException {
        bean.init(project);
        bean.openDrawerForCreate();
        bean.setDraftName("Matière");
        bean.selectDraftSource("branche");
        bean.setDraftThesaurusUrl("https://example.org/thesaurus");
        Vocabulary vocabulary = new Vocabulary();
        VocabularyDTO vocabularyDTO = VocabularyDTO.builder().build();
        when(vocabularyService.findOrCreateVocabularyOfUri("https://example.org/thesaurus")).thenReturn(vocabulary);
        when(vocabularyMapper.convert(vocabulary)).thenReturn(vocabularyDTO);
        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.testThesaurusConnection();
        }
        ConceptAutocompleteDetachedDTO ceramique = conceptResult("Céramique");
        bean.setDraftBrancheConcept(ceramique);

        CustomFieldSelectOne savedField = new CustomFieldSelectOne();
        savedField.setLabel("Matière");
        FormConfig formConfig = new FormConfig();
        when(tableFieldConfigService.createOrGetFormConfig(42L, ConfigurableTable.UE, "Céramique")).thenReturn(Optional.of(formConfig));
        when(tableFieldConfigService.getActiveAdditionalFields(42L, ConfigurableTable.UE, "Céramique")).thenReturn(List.of(savedField));

        bean.saveDrawer();

        verify(formConfigService).addConceptConfigFor(formConfig, savedField, ceramique.concept());
    }

    @Test
    void saveDrawer_shouldPersistTheSelectedCollectionViaFormConfigService() throws InvalidEndpointException {
        bean.init(project);
        bean.openDrawerForCreate();
        bean.setDraftName("Matière");
        bean.selectDraftSource("collection");
        bean.setDraftThesaurusUrl("https://example.org/thesaurus");
        Vocabulary vocabulary = new Vocabulary();
        VocabularyDTO vocabularyDTO = VocabularyDTO.builder().build();
        when(vocabularyService.findOrCreateVocabularyOfUri("https://example.org/thesaurus")).thenReturn(vocabulary);
        when(vocabularyMapper.convert(vocabulary)).thenReturn(vocabularyDTO);
        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.testThesaurusConnection();
        }
        ConceptCollectionDetachedDTO numismatique = collectionResult("Collection numismatique");
        when(conceptCollectionService.fetchCollectionsFromRemoteThesaurus(vocabularyDTO)).thenReturn(List.of(numismatique));
        bean.completeCollections("numismatique");
        bean.setDraftCollectionName("Collection numismatique");

        CustomFieldSelectOne savedField = new CustomFieldSelectOne();
        savedField.setLabel("Matière");
        FormConfig formConfig = new FormConfig();
        when(tableFieldConfigService.createOrGetFormConfig(42L, ConfigurableTable.UE, "Céramique")).thenReturn(Optional.of(formConfig));
        when(tableFieldConfigService.getActiveAdditionalFields(42L, ConfigurableTable.UE, "Céramique")).thenReturn(List.of(savedField));

        bean.saveDrawer();

        verify(formConfigService).addConceptConfigFor(formConfig, savedField, numismatique);
    }

    @Test
    void saveDrawer_shouldNotCallFormConfigServiceForThePrincipalSource() {
        bean.init(project);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder()
                .name("Catégorie").type(FieldType.SELECT_ONE).systemField(true).sourceLabel("CATEGORIE").build();
        bean.openDrawerForEdit(field);

        bean.saveDrawer();

        verifyNoInteractions(formConfigService);
    }

    @Test
    void closeDrawer_shouldResetSystemAndParamsState() {
        bean.init(project);
        TypeFieldFormConfig field = TypeFieldFormConfig.builder()
                .name("Catégorie").type(FieldType.SELECT_ONE).systemField(true).sourceLabel("CATEGORIE").build();
        bean.openDrawerForEdit(field);

        bean.closeDrawer();

        assertThat(bean.isDrawerOpen()).isFalse();
        assertThat(bean.isDraftIsSystem()).isFalse();
        assertThat(bean.getDraftFieldCode()).isNull();
        assertThat(bean.getDraftSource()).isNull();
    }

    @Test
    void reset_shouldClearAllState() {
        bean.init(project);

        bean.reset();

        assertThat(bean.getProject()).isNull();
        assertThat(bean.getTables()).isEmpty();
        assertThat(bean.getSelectedTable()).isNull();
        assertThat(bean.getSelectedTypeName()).isNull();
        assertThat(bean.getFormConfig()).isNull();
        assertThat(bean.getFieldsConfig()).isNull();
    }
}
