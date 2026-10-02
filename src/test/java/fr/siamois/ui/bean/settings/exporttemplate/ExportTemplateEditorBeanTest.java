package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptRef;
import fr.siamois.domain.models.exporttemplate.ExportTemplateJson;
import fr.siamois.domain.models.exporttemplate.InvalidExportTemplateException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver.FieldOption;
import fr.siamois.domain.services.exporttemplate.ExportTemplateService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ExportTemplateDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditColumn;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditRule;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditSheet;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditorBean.Item;
import fr.siamois.utils.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportTemplateEditorBeanTest {

    private static final String CONCEPT = "{\"thesaurus\":\"th230\",\"id\":\"4290928\"}";

    /** Une feuille UE à deux sources (unités d'enregistrement + relations) et une colonne à deux règles ciblées. */
    private static final String JSON = "{\"schemaVersion\":1,\"id\":\"u\",\"version\":\"1\",\"name\":\"Rapport\",\"sheets\":[{"
            + "\"name\":\"UE\",\"sources\":[{\"kind\":\"ENTITY\",\"entity\":\"RECORDING_UNIT\"},{\"kind\":\"TECHNICAL\",\"key\":\"STRATIGRAPHIC_RELATIONSHIP\"}],"
            + "\"columns\":[{\"header\":\"code\",\"rules\":["
            + "{\"type\":\"DIRECT\",\"sources\":[0],\"field\":{\"concept\":" + CONCEPT + "},\"path\":[\"project\"]},"
            + "{\"type\":\"DIRECT\",\"sources\":[1],\"field\":{\"column\":\"relationType\"}}]}]}]}";

    @Mock private ExportTemplateService service;
    @Mock private ExportFieldResolver resolver;
    @Mock private TableFieldConfigService tableFieldConfigService;
    @Mock private ActionUnitService actionUnitService;
    @Mock private ProfilePermissionService permissions;
    @Mock private SessionSettingsBean sessionSettingsBean;
    @Mock private LangBean langBean;
    @Mock private LabelBean labelBean;
    @Mock private RedirectBean redirectBean;

    private ExportTemplateEditorBean bean;
    private UserInfo userInfo;
    private PersonDTO person;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        bean = new ExportTemplateEditorBean(service, resolver, tableFieldConfigService, actionUnitService,
                permissions, sessionSettingsBean, langBean, labelBean, redirectBean);
        person = new PersonDTO();
        institution = new InstitutionDTO();
        userInfo = new UserInfo(institution, person, "fr");
        lenient().when(sessionSettingsBean.getUserInfo()).thenReturn(userInfo);
        lenient().when(sessionSettingsBean.getSelectedInstitution()).thenReturn(institution);
        lenient().when(langBean.msg(anyString())).thenAnswer(i -> i.getArgument(0));
        lenient().when(langBean.msg(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(permissions.hasInstancePermission(person, PermissionConstants.INSTANCE_MANAGE_SETTINGS)).thenReturn(true);
    }

    private void openTemplate(Long referenceProjectId) {
        ExportTemplateDTO dto = new ExportTemplateDTO(1L, "u", "Rapport", "1", referenceProjectId, Instant.now(), Instant.now());
        when(service.find(userInfo, 1L)).thenReturn(dto);
        when(service.getDefinition(userInfo, 1L)).thenReturn(ExportTemplateJson.parse(JSON));
        when(actionUnitService.findAllByInstitution(institution)).thenReturn(Set.of());
        assertThat(bean.open(1L)).contains("exportTemplateEditor.xhtml");
    }

    private static Concept concept(Long id, String thesaurus, String externalId) {
        Vocabulary v = new Vocabulary();
        v.setExternalVocabularyId(thesaurus);
        Concept c = new Concept();
        c.setId(id);
        c.setExternalId(externalId);
        c.setVocabulary(v);
        return c;
    }

    @Test
    void open_withoutManagePermission_isRefused() {
        when(permissions.hasInstancePermission(person, PermissionConstants.INSTANCE_MANAGE_SETTINGS)).thenReturn(false);
        when(permissions.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS)).thenReturn(false);
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            assertThat(bean.open(1L)).isNull();
            messages.verify(() -> MessageUtils.displayErrorMessage(langBean, "common.error.forbidden"));
        }
        assertThat(bean.getModel()).isNull();
    }

    @Test
    void open_loadsTheModelAndTheSelectedSheet() {
        openTemplate(null);

        assertThat(bean.getModel().getName()).isEqualTo("Rapport");
        assertThat(bean.getSelectedSheet().getName()).isEqualTo("UE");
        assertThat(bean.getTemplateId()).isEqualTo(1L);
    }

    @Test
    void checkOrRedirect_withoutModel_goesBackToTheList() {
        bean.checkOrRedirect();

        verify(redirectBean).redirectTo("/settings/export-templates");
    }

    @Test
    void addSheet_createsAValidMinimalSheetAndSelectsIt() {
        openTemplate(null);

        bean.addSheet();

        assertThat(bean.getModel().getSheets()).hasSize(2);
        EditSheet added = bean.getSelectedSheet();
        assertThat(added).isSameAs(bean.getModel().getSheets().get(1));
        assertThat(added.getSources()).hasSize(1);
        assertThat(added.getColumns()).singleElement().satisfies(c -> assertThat(c.getRules()).hasSize(1));
        ExportTemplateJson.parse(ExportTemplateJson.toJson(addAndConvert()));
    }

    private ExportTemplateDefinition addAndConvert() {
        return bean.getModel().toDefinition();
    }

    @Test
    void removeSource_reindexesRules_andDropsTheOnesThatOnlyTargetedIt() {
        openTemplate(null);

        bean.removeSource(bean.getSelectedSheet().getSources().get(0));

        EditSheet sheet = bean.getSelectedSheet();
        assertThat(sheet.getSources()).hasSize(1);
        List<EditRule> rules = sheet.getColumns().get(0).getRules();
        assertThat(rules).singleElement().satisfies(r -> assertThat(r.getSources()).containsExactly("0"));
    }

    @Test
    void removeSource_keepsRulesTargetingAllSources() {
        openTemplate(null);
        EditRule all = new EditRule();
        bean.getSelectedSheet().getColumns().get(0).getRules().add(all);

        bean.removeSource(bean.getSelectedSheet().getSources().get(1));

        assertThat(bean.getSelectedSheet().getColumns().get(0).getRules()).contains(all);
    }

    @Test
    void moveColumn_andRemoveColumn_updateTheSortBy() {
        openTemplate(null);
        bean.addColumn();
        EditSheet sheet = bean.getSelectedSheet();
        EditColumn added = sheet.getColumns().get(1);
        sheet.getSortBy().add(added.getHeader());

        bean.moveColumn(added, -1);
        assertThat(sheet.getColumns().get(0)).isSameAs(added);
        bean.moveColumn(added, -1);
        assertThat(sheet.getColumns().get(0)).isSameAs(added);

        bean.removeColumn(added);
        assertThat(sheet.getColumns()).hasSize(1);
        assertThat(sheet.getSortBy()).isEmpty();
    }

    @Test
    void fieldItems_followTheSourceAndThePathOfTheRule() {
        openTemplate(5L);
        when(resolver.listFields(ExportSubject.PROJECT, 5L)).thenReturn(List.of(
                new FieldOption("actionunit.field.oaCode", true, new ConceptRef("th230", "4290928", null))));
        EditRule onUnits = bean.getSelectedSheet().getColumns().get(0).getRules().get(0);
        EditRule onRelations = bean.getSelectedSheet().getColumns().get(0).getRules().get(1);

        List<Item> viaProject = bean.fieldItems(onUnits);
        List<Item> relationColumns = bean.fieldItems(onRelations);

        assertThat(viaProject).extracting(Item::value).contains("th230|4290928|");
        assertThat(relationColumns).extracting(Item::value).containsExactly("asynchronous", "relationType", "uncertain");
    }

    @Test
    void fieldItems_withAnInvalidPath_offersNothingButTheCurrentValue() {
        openTemplate(5L);
        EditRule rule = bean.getSelectedSheet().getColumns().get(0).getRules().get(0);
        rule.setPath("nope");

        List<Item> items = bean.fieldItems(rule);

        assertThat(items).extracting(Item::value).containsExactly("th230|4290928|");
        assertThat(items.get(0).label()).isEqualTo("exportTemplates.editor.unresolved");
    }

    @Test
    void typeItems_listTheConfiguredTypesOfTheReferenceProject_andKeepUnresolvedSelections() {
        openTemplate(5L);
        Concept type = concept(1L, "th230", "A");
        when(tableFieldConfigService.listConfiguredTypeConcepts(5L, ConfigurableTable.UE)).thenReturn(List.of(type));
        when(labelBean.findLabelOfConcept(type)).thenReturn("Creusement");
        var source = bean.getSelectedSheet().getSources().get(0);
        source.getTypeKeys().add("th230|ZZ|");

        List<Item> items = bean.typeItems(source);

        assertThat(items).extracting(Item::value).containsExactly("th230|A|", "th230|ZZ|");
        assertThat(items.get(0).label()).isEqualTo("Creusement");
    }

    @Test
    void typeItems_withoutReferenceProject_isEmpty() {
        openTemplate(null);

        assertThat(bean.typeItems(bean.getSelectedSheet().getSources().get(0))).isEmpty();
        verifyNoInteractions(tableFieldConfigService);
    }

    @Test
    void typeItems_withABlankEntity_doesNotFail() {
        openTemplate(5L);
        var source = bean.getSelectedSheet().getSources().get(0);
        source.setEntity("");

        assertThat(bean.typeItems(source)).isEmpty();
    }

    @Test
    void pathHint_listsTheNavigationsOfTheRuleSource() {
        openTemplate(null);
        EditRule onUnits = bean.getSelectedSheet().getColumns().get(0).getRules().get(0);
        EditRule onRelations = bean.getSelectedSheet().getColumns().get(0).getRules().get(1);

        assertThat(bean.pathHint(onUnits)).isEqualTo("project");
        assertThat(bean.pathHint(onRelations)).isEqualTo("unit1, unit2");
    }

    @Test
    void save_normalizesFieldKinds_checksTheRegistries_andStores() {
        openTemplate(null);
        EditRule onRelations = bean.getSelectedSheet().getColumns().get(0).getRules().get(1);
        onRelations.getField().setKind(ExportTemplateEditModel.FIELD_CONCEPT); // faux : la source est technique, sans chemin
        when(service.updateDefinition(eq(userInfo), eq(1L), any())).thenReturn(
                new ExportTemplateDTO(1L, "u", "Rapport", "1", null, Instant.now(), Instant.now()));
        when(service.getDefinition(userInfo, 1L)).thenReturn(ExportTemplateJson.parse(JSON));
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            bean.save();

            ArgumentCaptor<ExportTemplateDefinition> captor = ArgumentCaptor.forClass(ExportTemplateDefinition.class);
            verify(service).updateDefinition(eq(userInfo), eq(1L), captor.capture());
            assertThat(captor.getValue().sheets().get(0).columns().get(0).rules().get(1))
                    .isInstanceOfSatisfying(ExportTemplateDefinition.DirectRule.class,
                            r -> assertThat(r.field()).isInstanceOf(ExportTemplateDefinition.ColumnField.class));
            messages.verify(() -> MessageUtils.displayInfoMessage(langBean, "exportTemplates.editor.saved"));
        }
    }

    @Test
    void save_withAnUnknownNavigation_reportsTheProblemAndDoesNotStore() {
        openTemplate(null);
        bean.getSelectedSheet().getColumns().get(0).getRules().get(0).setPath("nope");
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            bean.save();

            verify(service, never()).updateDefinition(any(), any(), any());
            messages.verify(() -> MessageUtils.displayErrorMessage(eq(langBean), eq("exportTemplates.editor.error.problem"), any(Object[].class)));
        }
    }

    @Test
    void save_withAnIncompleteSource_reportsItAndDoesNotStore() {
        openTemplate(null);
        bean.getSelectedSheet().getSources().get(0).setEntity("");
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            bean.save();

            verify(service, never()).updateDefinition(any(), any(), any());
            messages.verify(() -> MessageUtils.displayErrorMessage(eq(langBean), eq("exportTemplates.editor.error.invalid"), any(Object[].class)));
        }
    }

    @Test
    void save_withAStructuralError_showsTheCodecMessage() {
        openTemplate(null);
        bean.getModel().setName(" ");
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            bean.save();

            verify(service, never()).updateDefinition(any(), any(), any());
            messages.verify(() -> MessageUtils.displayErrorMessage(eq(langBean), eq("exportTemplates.editor.error.invalid"), any(Object[].class)));
        }
    }

    @Test
    void save_whenTheServiceRefuses_showsTheCodecMessage() {
        openTemplate(null);
        when(service.updateDefinition(eq(userInfo), eq(1L), any())).thenThrow(new InvalidExportTemplateException("boom"));
        try (MockedStatic<MessageUtils> messages = mockStatic(MessageUtils.class)) {
            bean.save();

            messages.verify(() -> MessageUtils.displayErrorMessage(langBean, "exportTemplates.editor.error.invalid", "boom"));
        }
    }

    @Test
    void referenceProject_isExposedAsText_andPersistedOnChange() {
        openTemplate(7L);
        assertThat(bean.getReferenceProject()).isEqualTo("7");

        bean.setReferenceProject("");
        assertThat(bean.getReferenceProjectId()).isNull();
        bean.onReferenceProjectChange();

        verify(service).setReferenceProject(userInfo, 1L, null);
    }

    @Test
    void projectItems_areSortedAndLabelled() {
        ExportTemplateDTO dto = new ExportTemplateDTO(1L, "u", "Rapport", "1", null, Instant.now(), Instant.now());
        when(service.find(userInfo, 1L)).thenReturn(dto);
        when(service.getDefinition(userInfo, 1L)).thenReturn(ExportTemplateJson.parse(JSON));
        ActionUnitDTO b = new ActionUnitDTO();
        b.setId(2L);
        b.setName("Bravo");
        b.setFullIdentifier("C2");
        ActionUnitDTO a = new ActionUnitDTO();
        a.setId(1L);
        a.setName("Alpha");
        a.setFullIdentifier("C1");
        when(actionUnitService.findAllByInstitution(institution)).thenReturn(Set.of(b, a));

        bean.open(1L);

        assertThat(bean.getProjectItems()).extracting(Item::label).containsExactly("C1 — Alpha", "C2 — Bravo");
        assertThat(bean.getProjectItems()).extracting(Item::value).containsExactly("1", "2");
    }

    // ------------------------------------------------------------------ matrice et tiroir

    @Test
    void cellKind_followsTheRuleTargetingTheSource() {
        openTemplate(null);
        EditColumn column = bean.getSelectedSheet().getColumns().get(0);

        assertThat(bean.cellKind(column, 0)).isEqualTo("PATH");
        assertThat(bean.cellKind(column, 1)).isEqualTo("DIRECT");
        assertThat(bean.cellKind(column, 2)).isEmpty();
    }

    @Test
    void openCell_onEmptyCell_startsANewRuleTargetingOnlyThatSource() {
        openTemplate(null);
        EditColumn column = bean.getSelectedSheet().getColumns().get(0);

        bean.openCell(column, 2);

        assertThat(bean.isDrawerRuleMode()).isTrue();
        assertThat(bean.getDrawerOriginal()).isNull();
        assertThat(bean.getDrawerRule().getSources()).containsExactly("2");
    }

    @Test
    void applyDrawer_onExistingRule_replacesItOnlyWhenApplied() {
        openTemplate(null);
        EditColumn column = bean.getSelectedSheet().getColumns().get(0);
        EditRule original = column.getRules().get(1);

        bean.openCell(column, 1);
        bean.selectDrawerTab("CONSTANT");
        bean.getDrawerRule().setConstantValue("X");
        assertThat(original.getType()).isEqualTo("DIRECT");

        bean.applyDrawer();

        assertThat(column.getRules().get(1).getType()).isEqualTo("CONSTANT");
        assertThat(column.getRules().get(1).getConstantValue()).isEqualTo("X");
        assertThat(bean.isDrawerOpen()).isFalse();
    }

    @Test
    void applyDrawer_withoutAField_doesNotAddAnEmptyRule() {
        openTemplate(null);
        EditColumn column = bean.getSelectedSheet().getColumns().get(0);

        bean.openCell(column, 2);
        bean.applyDrawer();

        assertThat(column.getRules()).hasSize(2);
    }

    @Test
    void closeDrawer_discardsTheWorkingCopy() {
        openTemplate(null);
        EditColumn column = bean.getSelectedSheet().getColumns().get(0);

        bean.openCell(column, 1);
        bean.selectDrawerTab("CONSTANT");
        bean.closeDrawer();

        assertThat(column.getRules().get(1).getType()).isEqualTo("DIRECT");
    }

    @Test
    void clearCell_onSingleSourceRule_removesIt() {
        openTemplate(null);
        EditColumn column = bean.getSelectedSheet().getColumns().get(0);

        bean.openCell(column, 1);
        bean.clearCell();

        assertThat(column.getRules()).hasSize(1);
        assertThat(bean.cellKind(column, 1)).isEmpty();
    }

    @Test
    void clearCell_onRuleSharedByAllSources_keepsTheOthers() {
        openTemplate(null);
        EditColumn column = bean.getSelectedSheet().getColumns().get(0);
        column.getRules().get(1).setSources(new java.util.ArrayList<>());

        bean.openCell(column, 1);
        bean.clearCell();

        assertThat(column.getRules().get(1).getSources()).containsExactly("0");
    }

    @Test
    void pathTab_staysSelectedWhileThePathIsStillEmpty() {
        openTemplate(null);
        EditColumn column = bean.getSelectedSheet().getColumns().get(0);

        bean.openCell(column, 1);
        bean.selectDrawerTab("PATH");

        assertThat(bean.getDrawerTab()).isEqualTo("PATH");
    }

    @Test
    void addSourceAndEdit_opensTheDrawerOnTheNewSource() {
        openTemplate(null);

        bean.addSourceAndEdit();

        assertThat(bean.isDrawerSourceMode()).isTrue();
        assertThat(bean.getDrawerSource()).isSameAs(bean.getSelectedSheet().getSources().get(2));
    }

    @Test
    void backToList_redirectsToTheListPage() {
        bean.backToList();

        verify(redirectBean).redirectTo("/settings/export-templates");
    }
}
