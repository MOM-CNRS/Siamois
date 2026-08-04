package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.ActionUnitDTO;
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
import java.util.Map;

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
    private LangBean langBean;

    @Mock
    private RecordingUnitService recordingUnitService;

    @Mock
    private ActionUnitService actionUnitService;

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
        bean.setDraftBrancheConcept("Céramique");

        bean.selectDraftSource("collection");

        assertThat(bean.getDraftSource()).isEqualTo("collection");
        assertThat(bean.getDraftThesaurusUrl()).isNull();
        assertThat(bean.isDraftConnectionTested()).isFalse();
        assertThat(bean.getDraftBrancheConcept()).isNull();
    }

    @Test
    void testThesaurusConnection_shouldSetTestedOnlyWhenUrlIsNotBlank() {
        bean.init(project);
        bean.selectDraftSource("branche");

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.testThesaurusConnection();
            assertThat(bean.isDraftConnectionTested()).isFalse();
            messageUtilsMock.verify(() ->
                    MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_WARN, "projectTables.drawer.params.connectionMissingUrl"));

            bean.setDraftThesaurusUrl("https://example.org/thesaurus");
            bean.testThesaurusConnection();
            assertThat(bean.isDraftConnectionTested()).isTrue();
            messageUtilsMock.verify(() ->
                    MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "projectTables.drawer.params.connectionOk"));
        }
    }

    @Test
    void completeBrancheConcepts_and_completeCollections_shouldFilterTheMockedCatalogCaseInsensitively() {
        bean.init(project);

        assertThat(bean.completeBrancheConcepts("ramiq")).containsExactly("Céramique");
        assertThat(bean.completeBrancheConcepts("")).isNotEmpty();
        assertThat(bean.completeCollections("numismatique")).containsExactly("Collection numismatique");
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

    // ===== Identifiants (UE / _default only) =====

    private RuIdentifierResolver mockResolver(String code, String titleCode) {
        RuIdentifierResolver resolver = mock(RuIdentifierResolver.class);
        lenient().when(resolver.getCode()).thenReturn(code);
        lenient().when(resolver.getTitleCode()).thenReturn(titleCode);
        return resolver;
    }

    @Test
    void isIdentTabAvailable_shouldOnlyBeTrueForUeAndDefaultType() {
        bean.init(project);
        assertThat(bean.isIdentTabAvailable()).isFalse(); // UE + "Céramique" after init

        bean.selectType("_default");
        assertThat(bean.isIdentTabAvailable()).isTrue();

        bean.selectTable(ConfigurableTable.MOBILIER);
        assertThat(bean.isIdentTabAvailable()).isFalse();
    }

    @Test
    void identFormat_shouldRoundTripThroughSegmentsWhenUeDefaultIsSelected() {
        RuIdentifierResolver numUe = mockResolver("NUM_UE", "ru.identifier.title.number");
        RuIdentifierResolver typeUe = mockResolver("TYPE_UE", "ru.identifier.title.type");
        when(recordingUnitService.findAllIdentifierResolver()).thenReturn(Map.of("NUM_UE", numUe, "TYPE_UE", typeUe));
        when(recordingUnitService.findAllNumericalIdentifiersCode()).thenReturn(List.of("NUM_UE"));
        when(langBean.msg("ru.identifier.title.number")).thenReturn("Numéro de l'UE");
        when(langBean.msg("ru.identifier.title.type")).thenReturn("Type de l'UE");

        project.setRecordingUnitIdentifierFormat("{NUM_UE:000}-{TYPE_UE:XXX}");
        project.setMinRecordingUnitCode(1);
        project.setMaxRecordingUnitCode(999);

        bean.init(project);
        bean.selectType("_default");

        assertThat(bean.getIdentFirst()).isEqualTo(1);
        assertThat(bean.getIdentLast()).isEqualTo(999);
        assertThat(bean.getIdentSegments()).hasSize(3);
        assertThat(bean.getIdentSegments().get(0).isToken()).isTrue();
        assertThat(bean.getIdentSegments().get(0).getCode()).isEqualTo("NUM_UE");
        assertThat(bean.getIdentSegments().get(0).isNumeric()).isTrue();
        assertThat(bean.getIdentSegments().get(0).getDigits()).isEqualTo(3);
        assertThat(bean.getIdentSegments().get(1).isToken()).isFalse();
        assertThat(bean.getIdentSegments().get(1).getText()).isEqualTo("-");
        assertThat(bean.getIdentSegments().get(2).getCode()).isEqualTo("TYPE_UE");
        assertThat(bean.getIdentSegments().get(2).isNumeric()).isFalse();

        assertThat(bean.getIdentExample()).isNotEmpty();
    }

    @Test
    void addIdentTokenSegment_shouldMarkNumericTokensAndDefaultDigits() {
        RuIdentifierResolver numUe = mockResolver("NUM_UE", "ru.identifier.title.number");
        when(recordingUnitService.findAllIdentifierResolver()).thenReturn(Map.of("NUM_UE", numUe));
        when(recordingUnitService.findAllNumericalIdentifiersCode()).thenReturn(List.of("NUM_UE"));
        when(langBean.msg("ru.identifier.title.number")).thenReturn("Numéro de l'UE");

        bean.init(project);
        bean.selectType("_default");

        bean.addIdentTokenSegment("NUM_UE");

        assertThat(bean.getIdentSegments()).hasSize(1);
        IdentifierSegment seg = bean.getIdentSegments().get(0);
        assertThat(seg.isToken()).isTrue();
        assertThat(seg.isNumeric()).isTrue();
        assertThat(seg.getDigits()).isEqualTo(3);
        assertThat(seg.getLabel()).isEqualTo("Numéro de l'UE");
    }

    @Test
    void identSegmentEditing_shouldAddMoveAndRemoveSegments() {
        bean.init(project);
        bean.selectType("_default");

        bean.addIdentTextSegment();
        bean.getIdentSegments().get(0).setText("US-");
        bean.getIdentSegments().add(IdentifierSegment.builder().token(true).code("NUM_UE").numeric(true).digits(3).label("Numéro de l'UE").build());

        bean.moveIdentSegmentLeft(1);
        assertThat(bean.getIdentSegments().get(0).getCode()).isEqualTo("NUM_UE");
        assertThat(bean.getIdentSegments().get(1).getText()).isEqualTo("US-");

        bean.moveIdentSegmentRight(0);
        assertThat(bean.getIdentSegments().get(0).getText()).isEqualTo("US-");

        bean.removeIdentSegment(0);
        assertThat(bean.getIdentSegments()).hasSize(1);
        assertThat(bean.getIdentSegments().get(0).getCode()).isEqualTo("NUM_UE");
    }

    @Test
    void getIdentifierResolvers_shouldExcludeTypeUeAndTypeParent() {
        RuIdentifierResolver numUe = mockResolver("NUM_UE", "ru.identifier.title.number");
        RuIdentifierResolver typeUe = mockResolver("TYPE_UE", "ru.identifier.title.type");
        RuIdentifierResolver numParent = mockResolver("NUM_PARENT", "ru.identifier.title.number_parent");
        RuIdentifierResolver typeParent = mockResolver("TYPE_PARENT", "ru.identifier.title.type_parent");
        RuIdentifierResolver idUa = mockResolver("ID_UA", "ru.identifier.title.id_ua");
        when(recordingUnitService.findAllIdentifierResolver()).thenReturn(Map.of(
                "NUM_UE", numUe, "TYPE_UE", typeUe, "NUM_PARENT", numParent, "TYPE_PARENT", typeParent, "ID_UA", idUa));

        bean.init(project);
        bean.selectType("_default");

        assertThat(bean.getIdentifierResolvers())
                .extracting(RuIdentifierResolver::getCode)
                .containsExactlyInAnyOrder("NUM_UE", "NUM_PARENT", "ID_UA");
    }

    @Test
    void getIdentExample_shouldComputeSampleValuesWithoutCallingTheRealResolver() {
        bean.init(project);
        bean.selectType("_default");

        bean.getIdentSegments().add(IdentifierSegment.builder().token(true).code("NUM_UE").numeric(true).digits(3).build());
        bean.getIdentSegments().add(IdentifierSegment.builder().token(false).text("-").build());
        bean.getIdentSegments().add(IdentifierSegment.builder().token(true).code("ID_UA").build());

        assertThat(bean.getIdentExample()).isEqualTo("142-UA1");
        verifyNoInteractions(recordingUnitService);
    }

    @Test
    void saveIdentConfig_shouldRejectAFormatMissingNumUe() {
        when(recordingUnitService.findAllIdentifiersCode()).thenReturn(List.of("NUM_UE", "TYPE_UE"));
        when(recordingUnitService.findAllNumericalIdentifiersCode()).thenReturn(List.of("NUM_UE"));

        bean.init(project);
        bean.selectType("_default");
        bean.setIdentFirst(1);
        bean.setIdentLast(999);
        bean.getIdentSegments().add(IdentifierSegment.builder().token(true).code("TYPE_UE").numeric(false).digits(3).build());

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.saveIdentConfig();
            messageUtilsMock.verify(() ->
                    MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.missingNumUe"));
        }
        verify(actionUnitService, never()).save(any());
    }

    @Test
    void saveIdentConfig_shouldRejectInsufficientZeroPaddingForTheLastNumber() {
        when(recordingUnitService.findAllIdentifiersCode()).thenReturn(List.of("NUM_UE"));
        when(recordingUnitService.findAllNumericalIdentifiersCode()).thenReturn(List.of("NUM_UE"));

        bean.init(project);
        bean.selectType("_default");
        bean.setIdentFirst(1);
        bean.setIdentLast(12345);
        bean.getIdentSegments().add(IdentifierSegment.builder().token(true).code("NUM_UE").numeric(true).digits(3).build());

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.saveIdentConfig();
            messageUtilsMock.verify(() ->
                    MessageUtils.displayErrorMessage(langBean, "actionUnit.settings.error.insufficientDigits", "NUM_UE"));
        }
        verify(actionUnitService, never()).save(any());
    }

    @Test
    void saveIdentConfig_shouldPersistAndReloadOnValidFormat() {
        when(recordingUnitService.findAllIdentifiersCode()).thenReturn(List.of("NUM_UE"));
        when(recordingUnitService.findAllNumericalIdentifiersCode()).thenReturn(List.of("NUM_UE"));
        RuIdentifierResolver numUe = mockResolver("NUM_UE", "ru.identifier.title.number");
        when(recordingUnitService.findAllIdentifierResolver()).thenReturn(Map.of("NUM_UE", numUe));
        lenient().when(langBean.msg("ru.identifier.title.number")).thenReturn("Numéro de l'UE");
        lenient().when(langBean.getLanguageCode()).thenReturn("fr");

        bean.init(project);
        bean.selectType("_default");
        bean.setIdentFirst(1);
        bean.setIdentLast(999);
        bean.getIdentSegments().add(IdentifierSegment.builder().token(true).code("NUM_UE").numeric(true).digits(3).build());

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            bean.saveIdentConfig();
        }

        verify(actionUnitService).save(project);
        assertThat(project.getRecordingUnitIdentifierFormat()).isEqualTo("{NUM_UE:000}");
        assertThat(project.getMinRecordingUnitCode()).isEqualTo(1);
        assertThat(project.getMaxRecordingUnitCode()).isEqualTo(999);
        assertThat(project.getRecordingUnitIdentifierLang()).isEqualTo("fr");
    }
}
