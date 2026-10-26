package fr.siamois.ui.bean.settings.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.ConceptRef;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver.FieldOption;
import fr.siamois.domain.services.exporttemplate.ExportSourceReader;
import fr.siamois.domain.services.exporttemplate.ExportSourceReader.SourceCounts;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditColumn;
import fr.siamois.ui.bean.settings.exporttemplate.ExportTemplateEditModel.EditSheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Fenêtre « source de donnée » : choix de la table et des types, champs décochés par défaut, colonnes créées à la confirmation. */
class ExportTemplateEditorBeanSourceTest {

    private static final ConceptRef NAME_FIELD = new ConceptRef("th-sys", "name", null);
    private static final ConceptRef CODE_FIELD = new ConceptRef("th-sys", "code", null);

    private ExportTemplateEditorBean bean;
    private EditSheet sheet;

    @BeforeEach
    void setUp() {
        ExportFieldResolver resolver = mock(ExportFieldResolver.class);
        when(resolver.listFields(any(), any())).thenReturn(List.of(
                new FieldOption("Nom", false, NAME_FIELD), new FieldOption("Code", false, CODE_FIELD)));
        ExportSourceReader reader = mock(ExportSourceReader.class);
        when(reader.count(any(), anyLong())).thenReturn(new SourceCounts(3, Map.of()));
        LangBean lang = mock(LangBean.class);
        when(lang.msg(any(String.class))).thenAnswer(i -> i.getArgument(0));
        when(lang.msg(any(String.class), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        bean = new ExportTemplateEditorBean(null, null, resolver, null, null, reader, null, null, lang, null, null);
        ExportTemplateEditModel model = new ExportTemplateEditModel();
        sheet = new EditSheet();
        sheet.setName("Feuille 1");
        model.getSheets().add(sheet);
        bean.setModel(model);
    }

    private ExportSourceDialog.Table table(String key) {
        return bean.getSourceTree().stream().filter(t -> t.getKey().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void anEmptySheetHasNoSource_andNothingIsPickedByDefault() {
        assertThat(bean.isNoSources()).isTrue();
        assertThat(bean.getSourceChips()).isEmpty();
        assertThat(bean.isSourceDialogConfirmable()).isFalse();
    }

    @Test
    void pickingATableWithoutImportingFields_createsTheSourceButNoColumn() {
        bean.toggleTable(table(ExportSourceDialog.TABLE_PROJECT));
        assertThat(bean.getVisibleFieldRows()).hasSize(2).noneMatch(ExportSourceDialog.FieldRow::isChecked);

        bean.confirmSourceDialog();

        assertThat(sheet.getSources()).hasSize(1);
        assertThat(sheet.getSources().get(0).getKind()).isEqualTo(ExportTemplateEditModel.KIND_PROJECT);
        assertThat(sheet.getColumns()).isEmpty();
        assertThat(bean.isNoSources()).isFalse();
    }

    @Test
    void onlyTheCheckedFieldsBecomeColumns() {
        bean.toggleTable(table("RECORDING_UNIT"));
        bean.getSourceDialog().getFieldRows().get(1).setChecked(true);

        bean.confirmSourceDialog();

        assertThat(sheet.getColumns()).singleElement().satisfies(c -> {
            assertThat(c.getHeader()).isEqualTo("Code");
            assertThat(c.getRules()).singleElement().satisfies(r -> assertThat(r.getSources()).containsExactly("0"));
        });
        assertThat(sheet.getSources().get(0).getTypeKeys()).isEmpty();
    }

    @Test
    void theGlobalCheckboxHasThreeStates() {
        bean.toggleTable(table(ExportSourceDialog.TABLE_PROJECT));
        assertThat(bean.getFieldsSelectionState()).isEqualTo(ExportSourceDialog.STATE_NONE);

        bean.getSourceDialog().getFieldRows().get(0).setChecked(true);
        assertThat(bean.getFieldsSelectionState()).isEqualTo(ExportSourceDialog.STATE_SOME);

        bean.toggleAllFields();
        assertThat(bean.getFieldsSelectionState()).isEqualTo(ExportSourceDialog.STATE_ALL);

        bean.toggleAllFields();
        assertThat(bean.getFieldsSelectionState()).isEqualTo(ExportSourceDialog.STATE_NONE);
    }

    @Test
    void aFieldAlreadyReadByAColumnTargetsThatColumnByDefault_andIsNotDuplicatedForTheSameSource() {
        bean.toggleTable(table(ExportSourceDialog.TABLE_PROJECT));
        bean.getSourceDialog().getFieldRows().get(0).setChecked(true);
        bean.confirmSourceDialog();

        bean.openSourceDialog();
        bean.toggleTable(table("RECORDING_UNIT"));

        assertThat(bean.getSourceDialog().getFieldRows().get(0).getTarget()).isEqualTo("Nom");
        bean.getSourceDialog().getFieldRows().get(0).setChecked(true);
        bean.confirmSourceDialog();

        assertThat(sheet.getColumns()).hasSize(1);
        EditColumn column = sheet.getColumns().get(0);
        assertThat(column.getRules()).hasSize(2);
        assertThat(bean.getSourceChips()).extracting(ExportSourceDialog.Chip::getCovered).containsExactly(1, 1);
    }

    @Test
    void coverageBadgeFiltersTheColumnsTheSourceDoesNotCover() {
        bean.toggleTable(table(ExportSourceDialog.TABLE_PROJECT));
        bean.getSourceDialog().getFieldRows().get(0).setChecked(true);
        bean.confirmSourceDialog();
        bean.openSourceDialog();
        bean.toggleTable(table("SPECIMEN"));
        bean.getSourceDialog().getFieldRows().get(1).setChecked(true);
        bean.confirmSourceDialog();

        assertThat(bean.getSourceChips()).extracting(ExportSourceDialog.Chip::isFull).containsExactly(false, false);

        bean.showGaps(1);

        assertThat(bean.isGapFilter()).isTrue();
        assertThat(bean.getVisibleColumns()).extracting(EditColumn::getHeader).containsExactly("Nom");
        bean.clearGap();
        assertThat(bean.getVisibleColumns()).hasSize(2);
    }

    private void projectSourceWithColumn(String header, String fieldKey) {
        bean.toggleTable(table(ExportSourceDialog.TABLE_PROJECT));
        bean.getSourceDialog().getFieldRows().stream().filter(r -> r.getKey().contains("|" + fieldKey)).findFirst().orElseThrow().setChecked(true);
        bean.confirmSourceDialog();
        sheet.getColumns().get(sheet.getColumns().size() - 1).setHeader(header);
    }

    @Test
    void searchLooksAtTheColumnName_theField_theSourceTable_andTheConstants() {
        projectSourceWithColumn("Identifiant OA", "name");
        EditColumn constant = new EditColumn();
        constant.setHeader("Pays");
        ExportTemplateEditModel.EditRule rule = new ExportTemplateEditModel.EditRule();
        rule.setType(ExportTemplateEditModel.RULE_CONSTANT);
        rule.setConstantValue("France");
        constant.getRules().add(rule);
        sheet.getColumns().add(constant);

        bean.setColumnSearch("identifiant");
        assertThat(bean.getVisibleColumns()).extracting(EditColumn::getHeader).containsExactly("Identifiant OA");
        bean.setColumnSearch("france");
        assertThat(bean.getVisibleColumns()).extracting(EditColumn::getHeader).containsExactly("Pays");
        bean.setColumnSearch("name");
        assertThat(bean.getVisibleColumns()).extracting(EditColumn::getHeader).containsExactly("Identifiant OA");
        // a rule without explicit sources reads all of them: the constant column matches the source table too
        bean.setColumnSearch("source.PROJECT");
        assertThat(bean.getVisibleColumns()).extracting(EditColumn::getHeader).containsExactlyInAnyOrder("Identifiant OA", "Pays");
    }

    @Test
    void suggestionsOpenWithoutText_inThreeGroups() {
        projectSourceWithColumn("Identifiant OA", "name");

        assertThat(bean.getSuggestedColumns()).extracting(ExportTemplateEditorBean.Suggestion::label).containsExactly("Identifiant OA");
        assertThat(bean.getSuggestedMappedColumns()).isEmpty();
        assertThat(bean.getSuggestedFields()).extracting(ExportTemplateEditorBean.Suggestion::label).contains("Nom", "Code");

        bean.setColumnSearch("th-sys|name");
        assertThat(bean.getSuggestedColumns()).isEmpty();
        assertThat(bean.getSuggestedMappedColumns()).extracting(ExportTemplateEditorBean.Suggestion::label).containsExactly("Identifiant OA");
        assertThat(bean.getSuggestedFields()).extracting(ExportTemplateEditorBean.Suggestion::label).containsExactly("Nom");
        assertThat(bean.isNoSuggestion()).isFalse();

        bean.setColumnSearch("zzz");
        assertThat(bean.isNoSuggestion()).isTrue();
    }

    @Test
    void newFromAField_createsADirectColumnOnTheSourcesThatProvideIt() {
        projectSourceWithColumn("Identifiant OA", "name");
        bean.setColumnSearch("code");
        ExportTemplateEditorBean.Suggestion code = bean.getSuggestedFields().get(0);

        bean.createFromSuggestedField(code);

        assertThat(sheet.getColumns()).extracting(EditColumn::getHeader).containsExactly("Identifiant OA", "Code");
        EditColumn created = sheet.getColumns().get(1);
        assertThat(created.getRules()).singleElement().satisfies(r -> {
            assertThat(r.getType()).isEqualTo(ExportTemplateEditModel.RULE_DIRECT);
            assertThat(r.getSources()).containsExactly("0");
        });
        assertThat(bean.getColumnSearch()).isEmpty();
        assertThat(bean.getSelectedColumn()).isSameAs(created);
    }

    @Test
    void theFiltersAndTheSearchApplyToThePreviewColumnsToo() {
        projectSourceWithColumn("Identifiant OA", "name");
        EditColumn empty = new EditColumn();
        empty.setHeader("Vide");
        sheet.getColumns().add(empty);

        assertThat(bean.getPreviewColumnIndexes()).containsExactly(0, 1);
        bean.selectColumnFilter(ExportTemplateEditorBean.FILTER_MAPPED);
        assertThat(bean.getPreviewColumnIndexes()).containsExactly(0);
        bean.selectColumnFilter(ExportTemplateEditorBean.FILTER_ALL);
        bean.setColumnSearch("vide");
        assertThat(bean.getPreviewColumnIndexes()).containsExactly(1);
    }

    @Test
    void aPublishedTemplateRefusesTheDialog() throws Exception {
        java.lang.reflect.Field published = ExportTemplateEditorBean.class.getDeclaredField("published");
        published.setAccessible(true);
        published.setBoolean(bean, true);

        bean.openSourceDialog();
        bean.getSourceDialog().setTable(ExportSourceDialog.TABLE_PROJECT);
        bean.confirmSourceDialog();

        assertThat(sheet.getSources()).isEmpty();
    }
}
