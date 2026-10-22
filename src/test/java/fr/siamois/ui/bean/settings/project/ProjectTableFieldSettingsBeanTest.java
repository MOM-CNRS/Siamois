package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.form.config.FieldUnits;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.settings.tableconfig.ConceptSummary;
import fr.siamois.domain.models.settings.tableconfig.FieldOptions;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import org.mockito.ArgumentCaptor;
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

import static org.mockito.ArgumentMatchers.any;
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

    // --- field definition: type, multiple values, options, measurements ---

    private static UnitDefinitionDTO unitDto(long id, String symbol, UnitDefinition.Dimension dimension, double factor) {
        return UnitDefinitionDTO.builder().id(id).label(symbol).symbol(symbol).dimension(dimension).factorToBase(factor).build();
    }

    private static ConceptAutocompleteDTO measureConcept(long id, String label) {
        ConceptDTO concept = new ConceptDTO();
        concept.setId(id);
        return new ConceptAutocompleteDTO(concept, label, "fr");
    }

    private ProjectTableFieldSettingsBean beanWithUnits(TableFieldConfigService tableService, FormLayoutService layoutService) {
        ProjectTableFieldSettingsBean bean = bean(tableService, layoutService);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        bean.setProject(project);
        bean.setSelectedTable(ConfigurableTable.UE);
        bean.setSelectedTypeName("Creusement");
        UnitDefinitionService units = mock(UnitDefinitionService.class);
        when(units.findOptions()).thenReturn(List.of(
                unitDto(1L, "m²", UnitDefinition.Dimension.AREA, 1.0),
                unitDto(2L, "ha", UnitDefinition.Dimension.AREA, 10_000.0),
                unitDto(3L, "m", UnitDefinition.Dimension.LENGTH, 1.0)));
        bean.setUnitDefinitionService(units);
        return bean;
    }

    @Test
    void theTypeMenuOffersOneEntryPerFamily_andMultipleValuesPicksTheSiblingType() {
        ProjectTableFieldSettingsBean bean = bean(mock(TableFieldConfigService.class));
        bean.openDrawerForCreate();

        assertThat(bean.getFieldTypeOptions()).contains(FieldType.TEXT, FieldType.BOOLEAN, FieldType.SELECT_ONE)
                .doesNotContain(FieldType.SELECT_MULTIPLE);
        bean.setDraftTypeName("SELECT_ONE");
        assertThat(bean.isDraftSupportsMultiple()).isTrue();
        assertThat(bean.isDraftMultiple()).isFalse();

        bean.setDraftMultiple(true);
        assertThat(bean.getDraftType()).isEqualTo(FieldType.SELECT_MULTIPLE);
        assertThat(bean.getDraftTypeName()).isEqualTo("SELECT_ONE");
        assertThat(bean.isDraftMultiple()).isTrue();

        bean.setDraftTypeName("TEXT");
        assertThat(bean.isDraftSupportsMultiple()).isFalse();
        bean.setDraftMultiple(true);
        assertThat(bean.getDraftType()).isEqualTo(FieldType.TEXT);
        bean.setDraftTypeName(null);
        assertThat(bean.getDraftType()).isNull();
        assertThat(bean.getDraftTypeName()).isNull();
    }

    @Test
    void changingTheTypeOfASavedFieldIsFlagged() {
        ProjectTableFieldSettingsBean bean = bean(mock(TableFieldConfigService.class));
        bean.openDrawerForEdit(TypeFieldFormConfig.builder().name("Couleur").type(FieldType.TEXT).build());
        assertThat(bean.isDraftTypeChanged()).isFalse();

        bean.setDraftTypeName("INTEGER");

        assertThat(bean.isDraftTypeChanged()).isTrue();
        assertThat(bean.isDraftBoolean()).isFalse();
        assertThat(bean.isDraftMeasurement()).isFalse();
    }

    @Test
    void theUnitCheckboxesMayHandBackIdsOfAnyTypeAndTheyAreKeptAsText() {
        ProjectTableFieldSettingsBean bean = bean(mock(TableFieldConfigService.class));
        List<Object> asJsfDoes = new java.util.ArrayList<>(List.of(1L, 2L));
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<String> typedAsStrings = (List) asJsfDoes;

        bean.setDraftUnitIds(typedAsStrings);
        assertThat(bean.getDraftUnitIds()).containsExactly("1", "2");
        bean.setDraftUnitIds(null);
        assertThat(bean.getDraftUnitIds()).isEmpty();
    }

    @Test
    void aMeasurementOffersTheUnitsOfTheChosenDimension_andChangingTheDimensionDropsThePickedOnes() {
        ProjectTableFieldSettingsBean bean = beanWithUnits(mock(TableFieldConfigService.class), mock(FormLayoutService.class));
        bean.openDrawerForCreate();
        bean.setDraftTypeName("MEASUREMENT");

        assertThat(bean.getDimensionOptions()).containsExactly(UnitDefinition.Dimension.LENGTH, UnitDefinition.Dimension.AREA);
        assertThat(bean.dimensionLabel(UnitDefinition.Dimension.AREA)).isEqualTo("projectTables.drawer.dimension.AREA");
        assertThat(bean.getUnitOptions()).isEmpty();

        bean.setDraftDimensionName("AREA");
        bean.setDraftUnitIds(List.of("2", "1"));
        assertThat(bean.getDraftDimensionName()).isEqualTo("AREA");
        assertThat(bean.getUnitOptions()).extracting(UnitDefinitionDTO::getSymbol).containsExactly("m²", "ha");
        assertThat(bean.getPickedUnitOptions()).extracting(UnitDefinitionDTO::getSymbol).containsExactly("m²", "ha");

        bean.setDraftDimensionName("AREA");
        assertThat(bean.getDraftUnitIds()).containsExactly("2", "1");
        bean.setDraftDimensionName("LENGTH");
        assertThat(bean.getDraftUnitIds()).isEmpty();
        bean.setDraftDimensionName("");
        assertThat(bean.getDraftDimensionName()).isNull();
    }

    @Test
    void openingAMeasurementLoadsItsUnitsAndConcepts() {
        ProjectTableFieldSettingsBean bean = beanWithUnits(mock(TableFieldConfigService.class), mock(FormLayoutService.class));

        bean.openDrawerForEdit(TypeFieldFormConfig.builder().name("Surface").type(FieldType.MEASUREMENT).systemField(false)
                .options(FieldOptions.units(2L, java.util.Set.of(1L, 2L)))
                .measureType(new ConceptSummary(50L, "Surface", "")).measureNature(new ConceptSummary(51L, "fouillée", "")).build());

        assertThat(bean.getDraftDimensionName()).isEqualTo("AREA");
        assertThat(bean.getDraftUnitIds()).containsExactlyInAnyOrder("1", "2");
        assertThat(bean.getDraftDefaultUnitId()).isEqualTo("2");
        assertThat(bean.isDraftMeasureSelectable()).isTrue();
        assertThat(bean.isDraftDefinedByMeasureConcepts()).isFalse();
        assertThat(bean.isDraftNameDerived()).isTrue();
        assertThat(bean.getDraftMeasureTypeLabel()).isEqualTo("Surface");
        assertThat(bean.getDraftDisplayName()).isEqualTo("Surface fouillée");
    }

    @Test
    void aSystemMeasurementIsShownByItsConceptsReadOnly_andByItsOwnNameWhenItHasNone() {
        ProjectTableFieldSettingsBean bean = bean(mock(TableFieldConfigService.class));

        bean.openDrawerForEdit(TypeFieldFormConfig.builder().name("recordingunit.property.zInf").type(FieldType.MEASUREMENT)
                .systemField(true).build());

        assertThat(bean.isDraftDefinedByMeasureConcepts()).isTrue();
        assertThat(bean.isDraftMeasureSelectable()).isFalse();
        assertThat(bean.getDraftMeasureTypeName()).isEqualTo("recordingunit.property.zInf");
        assertThat(bean.getDraftMeasureNatureName()).isEmpty();
        assertThat(bean.getDraftDisplayName()).isEqualTo("recordingunit.property.zInf");

        bean.setDraftMeasureType(new ConceptSummary(1L, "Altitude", ""));
        bean.setDraftMeasureNature(new ConceptSummary(2L, "inférieure", ""));
        assertThat(bean.getDraftDisplayName()).isEqualTo("Altitude inférieure");
    }

    @Test
    void measureConceptsAreSearchedInTheirOwnVocabularies_andBackTheLabelPicked() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        when(tableService.measureConcepts(42L, "SIAMD.TYPE", "su")).thenReturn(List.of(measureConcept(50L, "Surface")));
        when(tableService.measureConcepts(42L, "SIAMD.NATURE", "fo")).thenReturn(List.of(measureConcept(51L, "fouillée")));
        when(tableService.measureConcepts(42L, "SIAMD.NATURE", "boom")).thenThrow(new IllegalStateException("down"));
        ProjectTableFieldSettingsBean bean = beanWithUnits(tableService, mock(FormLayoutService.class));

        assertThat(bean.completeMeasureTypes("su")).containsExactly("Surface");
        assertThat(bean.completeMeasureNatures("fo")).containsExactly("fouillée");
        assertThat(bean.completeMeasureNatures("boom")).isEmpty();
        assertThat(bean.getDrawerErrorMessage()).isEqualTo("common.error.internal");
    }

    @Test
    void savingANewMeasurementNeedsWhatItMeasures() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        ProjectTableFieldSettingsBean bean = beanWithUnits(tableService, mock(FormLayoutService.class));
        bean.openDrawerForCreate();
        bean.setDraftTypeName("MEASUREMENT");

        bean.saveDrawerIfEditable();

        assertThat(bean.getDrawerErrorMessage()).isEqualTo("projectTables.drawer.measure.typeRequired");
        verify(tableService, never()).createField(org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void savingANewMeasurementNeedsAtLeastOneUnit() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        when(tableService.measureConcepts(42L, "SIAMD.TYPE", "su")).thenReturn(List.of(measureConcept(50L, "Surface")));
        ProjectTableFieldSettingsBean bean = beanWithUnits(tableService, mock(FormLayoutService.class));
        bean.openDrawerForCreate();
        bean.setDraftTypeName("MEASUREMENT");
        bean.completeMeasureTypes("su");
        bean.setDraftMeasureTypeLabel("Surface");

        bean.saveDrawerIfEditable();

        assertThat(bean.getDrawerErrorMessage()).isEqualTo("projectTables.drawer.units.required");
    }

    @Test
    void aNewMeasurementIsNamedAfterItsTypeAndNature_andItsUnitsGoToTheFormConfiguration() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        when(tableService.measureConcepts(42L, "SIAMD.TYPE", "su")).thenReturn(List.of(measureConcept(50L, "Surface")));
        when(tableService.measureConcepts(42L, "SIAMD.NATURE", "fo")).thenReturn(List.of(measureConcept(51L, "fouillée")));
        CustomFieldMeasurement created = CustomFieldMeasurement.builder().id(77L).label("Surface fouillée").build();
        when(tableService.findField(42L, ConfigurableTable.UE, "Creusement", "Surface fouillée")).thenReturn(java.util.Optional.of(created));
        FormLayoutService layout = mock(FormLayoutService.class);
        ProjectTableFieldSettingsBean bean = beanWithUnits(tableService, layout);
        bean.openDrawerForCreate();
        bean.setDraftTypeName("MEASUREMENT");
        bean.completeMeasureTypes("su");
        bean.completeMeasureNatures("fo");
        bean.setDraftMeasureTypeLabel("Surface");
        bean.setDraftMeasureNatureLabel("fouillée");
        bean.setDraftDimensionName("AREA");
        bean.setDraftUnitIds(List.of("1", "2"));
        bean.setDraftDefaultUnitId("2");

        saveWithAFacesContext(bean);

        ArgumentCaptor<FieldOptions> options = ArgumentCaptor.forClass(FieldOptions.class);
        verify(tableService).createField(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.eq(ConfigurableTable.UE),
                org.mockito.ArgumentMatchers.eq("Creusement"), org.mockito.ArgumentMatchers.eq("Surface fouillée"),
                org.mockito.ArgumentMatchers.eq(FieldType.MEASUREMENT), any(), options.capture());
        assertThat(options.getValue().measureTypeConceptId()).isEqualTo(50L);
        assertThat(options.getValue().measureNatureConceptId()).isEqualTo(51L);
        assertThat(options.getValue().defaultUnitId()).isEqualTo(2L);
        ArgumentCaptor<FieldUnits> units = ArgumentCaptor.forClass(FieldUnits.class);
        verify(layout).saveUnits(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.eq(ConfigurableTable.UE),
                org.mockito.ArgumentMatchers.eq("Creusement"), org.mockito.ArgumentMatchers.eq(77L), units.capture());
        assertThat(units.getValue().symbols()).containsExactlyInAnyOrder("m²", "ha");
        assertThat(units.getValue().defaultSymbol()).isEqualTo("ha");
    }

    @Test
    void savingASystemMeasurementOnlyWritesItsUnits() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        CustomFieldMeasurement zInf = CustomFieldMeasurement.builder().id(-322L).label("recordingunit.property.zInf").build();
        when(tableService.findField(42L, ConfigurableTable.UE, "Creusement", "recordingunit.property.zInf")).thenReturn(java.util.Optional.of(zInf));
        FormLayoutService layout = mock(FormLayoutService.class);
        ProjectTableFieldSettingsBean bean = beanWithUnits(tableService, layout);
        bean.openDrawerForEdit(TypeFieldFormConfig.builder().name("recordingunit.property.zInf").type(FieldType.MEASUREMENT)
                .systemField(true).options(FieldOptions.units(3L, java.util.Set.of(3L))).build());

        saveWithAFacesContext(bean);

        verify(layout).saveUnits(org.mockito.ArgumentMatchers.eq(42L), any(), any(), org.mockito.ArgumentMatchers.eq(-322L), any());
    }

    @Test
    void aTableOwnPageHasNoTypeOpen_andDescribesWhatTheTableIsFor() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        when(tableService.listTypes(42L, ConfigurableTable.MOBILIER)).thenReturn(List.of());
        ProjectTableFieldSettingsBean bean = beanWithUnits(tableService, mock(FormLayoutService.class));

        bean.selectTableOnly(ConfigurableTable.MOBILIER);

        assertThat(bean.getSelectedTable()).isEqualTo(ConfigurableTable.MOBILIER);
        assertThat(bean.getSelectedTypeName()).isNull();
        assertThat(bean.getSelectedTableDescription()).isEqualTo("projectTables.table.description.MOBILIER");
    }

    @Test
    void noTableSelectedHasNoDescription() {
        assertThat(bean(mock(TableFieldConfigService.class)).getSelectedTableDescription()).isEmpty();
    }

    @Test
    void addingAFieldFromTheTreeSelectsItsTypeThenOpensThePicker() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        ProjectTableFieldSettingsBean bean = beanWithUnits(tableService, mock(FormLayoutService.class));

        bean.addFieldToType(ConfigurableTable.UE, "Creusement");

        assertThat(bean.isPickerOpen()).isTrue();
        assertThat(bean.getSelectedTypeName()).isEqualTo("Creusement");
    }

    /** A successful save tells the user so, through the Faces context and PrimeFaces: neither exists outside a request. */
    private static void saveWithAFacesContext(ProjectTableFieldSettingsBean bean) {
        try (org.mockito.MockedStatic<fr.siamois.utils.MessageUtils> messages = org.mockito.Mockito.mockStatic(fr.siamois.utils.MessageUtils.class)) {
            bean.saveDrawerIfEditable();
        }
    }

    private ProjectTableFieldSettingsBean bean(TableFieldConfigService tableService) {
        return bean(tableService, mock(FormLayoutService.class));
    }

    private ProjectTableFieldSettingsBean bean(TableFieldConfigService tableService, FormLayoutService layoutService) {
        LangBean langBean = mock(LangBean.class);
        when(langBean.msg(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(langBean.msg(org.mockito.ArgumentMatchers.anyString(), any(Object[].class)))
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
                layoutService,
                mock(fr.siamois.domain.services.vocabulary.FieldConfigurationService.class));
    }
}
