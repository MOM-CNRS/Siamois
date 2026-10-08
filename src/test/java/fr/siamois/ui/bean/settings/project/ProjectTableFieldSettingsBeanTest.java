package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.LayoutRow;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.form.layout.FormLayoutService;
import fr.siamois.domain.services.identifier.IdentifierResolver;
import fr.siamois.domain.services.identifier.IdentifierResolverRegistry;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptCollectionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.ui.bean.LangBean;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectTableFieldSettingsBeanTest {

    private static TypeFieldFormConfig field(String name, boolean active) {
        return TypeFieldFormConfig.builder().name(name).systemField(true).active(active).sourceLabel("common.action.noSource").build();
    }

    private ProjectTableFieldSettingsBean beanOn(FormLayoutService layout) {
        ProjectTableFieldSettingsBean bean = bean(mock(TableFieldConfigService.class), layout);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        bean.setProject(project);
        bean.setSelectedTable(ConfigurableTable.UE);
        return bean;
    }

    @Test
    void layoutRows_shouldListGroupsAndFields_andFlagThePivotTypeField() {
        FormLayoutService layout = mock(FormLayoutService.class);
        TypeFieldFormConfig pivot = TypeFieldFormConfig.builder().name("recordingunit.property.type").systemField(true)
                .active(true).sourceLabel(ConfigurableTable.UE.getFieldCode()).build();
        TypeFieldFormConfig removed = field("recordingunit.field.description", false);
        when(layout.rowsOf(42L, ConfigurableTable.UE, "Creusement")).thenReturn(List.of(
                LayoutRow.header(1L, "A"), LayoutRow.of(pivot, FieldWidth.QUARTER), LayoutRow.of(removed, FieldWidth.HALF)));

        ProjectTableFieldSettingsBean bean = beanOn(layout);
        bean.selectType("Creusement");

        // a field taken out of the type (stored as inactive) is simply not listed
        assertThat(bean.getLayoutRows()).hasSize(2);
        assertThat(bean.getFieldCount()).isEqualTo(1);
        assertThat(bean.isPivotField(pivot)).isTrue();
        assertThat(bean.isPivotField(removed)).isFalse();
    }

    @Test
    void onLayoutReorder_shouldSendGroupsWithTheirFieldsInOrder() {
        FormLayoutService layout = mock(FormLayoutService.class);
        ProjectTableFieldSettingsBean bean = beanOn(layout);
        bean.setSelectedTypeName("Creusement");
        bean.setLayoutRows(new java.util.ArrayList<>(List.of(
                LayoutRow.header(1L, "G1"), LayoutRow.of(field("a", true), FieldWidth.QUARTER),
                LayoutRow.header(null, "G2"), LayoutRow.of(field("b", true), FieldWidth.QUARTER),
                LayoutRow.of(field("c", true), FieldWidth.QUARTER))));

        bean.onLayoutReorder();

        verify(layout).saveArrangement(42L, ConfigurableTable.UE, "Creusement", List.of(
                new FormLayoutService.GroupSpec(1L, "G1", List.of("a")),
                new FormLayoutService.GroupSpec(null, "G2", List.of("b", "c"))));
    }

    @Test
    void onLayoutReorder_shouldRefuseAFieldDroppedAboveTheFirstGroup() {
        FormLayoutService layout = mock(FormLayoutService.class);
        ProjectTableFieldSettingsBean bean = beanOn(layout);
        bean.setSelectedTypeName("Creusement");
        bean.setLayoutRows(new java.util.ArrayList<>(List.of(
                LayoutRow.of(field("a", true), FieldWidth.QUARTER), LayoutRow.header(1L, "G1"))));

        try (org.mockito.MockedStatic<fr.siamois.utils.MessageUtils> ignored =
                     org.mockito.Mockito.mockStatic(fr.siamois.utils.MessageUtils.class)) {
            bean.onLayoutReorder();
        }

        verify(layout, never())
                .saveArrangement(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteGroup_shouldRefuseANonEmptyGroup_andDropAnEmptyOne() {
        FormLayoutService layout = mock(FormLayoutService.class);
        ProjectTableFieldSettingsBean bean = beanOn(layout);
        bean.setSelectedTypeName("Creusement");
        LayoutRow g1 = LayoutRow.header(1L, "G1");
        LayoutRow g2 = LayoutRow.header(2L, "G2");
        bean.setLayoutRows(new java.util.ArrayList<>(List.of(g1, LayoutRow.of(field("a", true), FieldWidth.QUARTER), g2)));

        try (org.mockito.MockedStatic<fr.siamois.utils.MessageUtils> ignored =
                     org.mockito.Mockito.mockStatic(fr.siamois.utils.MessageUtils.class)) {
            bean.deleteGroup(g1);
        }
        assertThat(bean.getLayoutRows()).hasSize(3);

        bean.deleteGroup(g2);
        assertThat(bean.getLayoutRows()).doesNotContain(g2);
    }

    @Test
    void moveGroup_shouldMoveTheGroupWithItsFields() {
        FormLayoutService layout = mock(FormLayoutService.class);
        ProjectTableFieldSettingsBean bean = beanOn(layout);
        bean.setSelectedTypeName("Creusement");
        LayoutRow g1 = LayoutRow.header(1L, "G1");
        LayoutRow a = LayoutRow.of(field("a", true), FieldWidth.QUARTER);
        LayoutRow g2 = LayoutRow.header(2L, "G2");
        LayoutRow b = LayoutRow.of(field("b", true), FieldWidth.QUARTER);
        bean.setLayoutRows(new java.util.ArrayList<>(List.of(g1, a, g2, b)));

        bean.moveGroup(g2, -1);

        verify(layout).saveArrangement(42L, ConfigurableTable.UE, "Creusement", List.of(
                new FormLayoutService.GroupSpec(2L, "G2", List.of("b")),
                new FormLayoutService.GroupSpec(1L, "G1", List.of("a"))));
    }

    @Test
    void identifierTab_shouldLoadSelectedTypeConfigAndUseItsTableCatalog() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        TypeFormConfig config = TypeFormConfig.builder()
                .typeName("Ceramique")
                .identifierFormat("M-{NUM_MOBILIER:000}-{ID_UE}")
                .minCode(0)
                .maxCode(999)
                .build();
        when(tableService.getFormConfig(42L, ConfigurableTable.MOBILIER, "Ceramique")).thenReturn(config);

        ProjectTableFieldSettingsBean bean = bean(tableService);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        bean.setProject(project);
        bean.setSelectedTable(ConfigurableTable.MOBILIER);
        bean.selectType("Ceramique");

        assertThat(bean.isIdentTabAvailable()).isTrue();
        assertThat(bean.getIdentFirst()).isZero();
        assertThat(bean.getIdentLast()).isEqualTo(999);
        assertThat(bean.getIdentifierResolvers()).extracting(IdentifierResolver::code)
                .containsExactly("NUM_MOBILIER", "NUM_PARENT", "ID_PARENT", "NUM_UE", "ID_UE", "ID_UA");
        assertThat(bean.getIdentExample()).isEqualTo("M-027-XXX");
    }

    @Test
    void identifierTab_shouldAlsoBeAvailableForDefaultUeConfig() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        when(tableService.getFormConfig(42L, ConfigurableTable.UE, "_default"))
                .thenReturn(TypeFormConfig.builder()
                        .typeName("_default")
                        .identifierFormat("{NUM_UE:000}-{NUM_PARENT:00}-{ID_PARENT}-{NUM_USPATIAL:0000}")
                        .minCode(0)
                        .maxCode(999)
                        .build());

        ProjectTableFieldSettingsBean bean = bean(tableService);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        bean.setProject(project);
        bean.setSelectedTable(ConfigurableTable.UE);
        bean.selectType("_default");

        assertThat(bean.getIdentExample()).isEqualTo("142-00-XXX-0000");
    }

    @Test
    void removeField_onASystemField_setsItAsideAndKeepsItInTheCatalog() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        FormLayoutService layout = mock(FormLayoutService.class);
        ProjectTableFieldSettingsBean bean = bean(tableService, layout);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        bean.setProject(project);
        bean.setSelectedTable(ConfigurableTable.UE);
        bean.setSelectedTypeName("Creusement");
        TypeFieldFormConfig description = field("recordingunit.field.description", true);

        bean.removeField(description);

        verify(tableService).setFieldActive(42L, ConfigurableTable.UE, "Creusement", "recordingunit.field.description", false);
    }

    @Test
    void removeField_refusesThePivotTypeField() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        ProjectTableFieldSettingsBean bean = bean(tableService, mock(FormLayoutService.class));
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        bean.setProject(project);
        bean.setSelectedTable(ConfigurableTable.UE);
        bean.setSelectedTypeName("Creusement");
        TypeFieldFormConfig pivot = TypeFieldFormConfig.builder().name("recordingunit.property.type").systemField(true)
                .active(true).sourceLabel(ConfigurableTable.UE.getFieldCode()).build();

        bean.removeField(pivot);

        org.mockito.Mockito.verifyNoInteractions(tableService);
    }

    @Test
    void foldingAGroup_hidesItsFieldsButKeepsTheirRowsInTheList() {
        FormLayoutService layout = mock(FormLayoutService.class);
        TypeFieldFormConfig a = field("a", true);
        TypeFieldFormConfig b = field("b", true);
        LayoutRow g1 = LayoutRow.header(1L, "G1");
        LayoutRow ra = LayoutRow.of(a, FieldWidth.QUARTER);
        LayoutRow g2 = LayoutRow.header(2L, "G2");
        LayoutRow rb = LayoutRow.of(b, FieldWidth.QUARTER);
        when(layout.rowsOf(42L, ConfigurableTable.UE, "Creusement")).thenReturn(List.of(g1, ra, g2, rb));
        ProjectTableFieldSettingsBean bean = beanOn(layout);
        bean.selectType("Creusement");
        LayoutRow first = bean.getLayoutRows().get(0);
        LayoutRow firstField = bean.getLayoutRows().get(1);
        LayoutRow secondField = bean.getLayoutRows().get(3);

        bean.toggleGroup(first);

        assertThat(bean.isGroupCollapsed(first)).isTrue();
        assertThat(bean.isRowHidden(firstField)).isTrue();
        assertThat(bean.isRowHidden(secondField)).isFalse();
        assertThat(bean.getLayoutRows()).hasSize(4);

        bean.toggleGroup(first);
        assertThat(bean.isRowHidden(firstField)).isFalse();
    }

    @Test
    void searchingByName_showsOnlyMatchingFields_andTheirGroups() {
        FormLayoutService layout = mock(FormLayoutService.class);
        when(layout.rowsOf(42L, ConfigurableTable.UE, "Creusement")).thenReturn(List.of(
                LayoutRow.header(1L, "G1"), LayoutRow.of(field("alpha", true), FieldWidth.QUARTER),
                LayoutRow.header(2L, "G2"), LayoutRow.of(field("beta", true), FieldWidth.QUARTER)));
        ProjectTableFieldSettingsBean bean = beanOn(layout);
        bean.selectType("Creusement");
        LayoutRow group1 = bean.getLayoutRows().get(0);
        LayoutRow alpha = bean.getLayoutRows().get(1);
        LayoutRow group2 = bean.getLayoutRows().get(2);
        LayoutRow beta = bean.getLayoutRows().get(3);

        bean.setFieldSearch("ALP");
        bean.onFieldSearch();

        assertThat(bean.isRowHidden(alpha)).isFalse();
        assertThat(bean.isRowHidden(group1)).isFalse();
        assertThat(bean.isRowHidden(beta)).isTrue();
        assertThat(bean.isRowHidden(group2)).isTrue();
    }

    private ProjectTableFieldSettingsBean bean(TableFieldConfigService tableService) {
        return bean(tableService, mock(FormLayoutService.class));
    }

    private ProjectTableFieldSettingsBean bean(TableFieldConfigService tableService, FormLayoutService layoutService) {
        LangBean langBean = mock(LangBean.class);
        when(langBean.msg(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new ProjectTableFieldSettingsBean(
                tableService,
                mock(FormConfigService.class),
                mock(ConceptService.class),
                mock(ConceptCollectionService.class),
                mock(VocabularyService.class),
                mock(VocabularyMapper.class),
                mock(LabelService.class),
                langBean,
                new IdentifierResolverRegistry(),
                layoutService);
    }
}
