package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.RelationColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import static fr.siamois.ui.table.definitions.TableDefinitions.column;
import static fr.siamois.ui.table.definitions.TableDefinitions.systemField;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class RecordingUnitTableDefinitionFactory {

    public static final String THIS = "@this";
    public static final String PF_BUI_CONTENT_SHOW = "PF('buiContent').show()";
    public static final String PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP = "PF('buiContent').hide()";
    public static final String BI_BI_EYE = "bi bi-eye";
    public static final String BI_BI_PLUS_SQUARE = "bi bi-plus-square";

    private RecordingUnitTableDefinitionFactory() {}

    /**
     * Applies the standard RecordingUnit columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<RecordingUnitDTO, ?> tableModel) {
        if (tableModel == null) {
            return;
        }
        applyTo(tableModel.getTableDefinition());
    }

    /**
     * The columns of the table on their own, with no view model to apply them to.
     *
     * @return a fresh definition carrying the table's standard columns
     */
    public static TableDefinition definition() {
        TableDefinition definition = new TableDefinition();
        applyTo(definition);
        return definition;
    }

    private static void applyTo(TableDefinition definition) {

        CustomField recordingUnitIdField = systemField(ConfigurableTable.UE, "fullIdentifier");
        CustomField typeField = systemField(ConfigurableTable.UE, "type");
        CustomField matrixColor = systemField(ConfigurableTable.UE, "matrixColor");
        CustomField dateField = systemField(ConfigurableTable.UE, "openingDate");
        CustomField authorField = systemField(ConfigurableTable.UE, "author");
        CustomField contributorsField = systemField(ConfigurableTable.UE, "contributors");
        CustomField actionField = systemField(ConfigurableTable.UE, "actionUnit");
        CustomField spatialField = systemField(ConfigurableTable.UE, "spatialUnit");
        CustomField isPartOfField = systemField(ConfigurableTable.UE, "parents");
        CustomField containsField = systemField(ConfigurableTable.UE, "children");
        CustomField phasesField = systemField(ConfigurableTable.UE, "phases");

        definition.setCommandLinkColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.recordingunit.column.identifier")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(true)
                        .filterable(true)
                        .sortField("full_identifier")

                        .iconClass("bi bi-pencil-square")
                        .chipColor("var(--ground-main-color)")
                        .valueKey("fullIdentifier")
                        .editable(true)

                        // What to do on click (Pattern A key)
                        .action(TableColumnAction.GO_TO_RECORDING_UNIT)

                        // CommandLink behavior
                        .processExpr(THIS)
                        .updateExpr(THIS)
                        .onstartJs(PF_BUI_CONTENT_SHOW)
                        .oncompleteJs(PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP)
                        .build()
        );
        definition.addColumn(
                FormFieldColumn.builder()
                        .id("identifier")
                        .headerKey("recordingunit.field.identifier")
                        .field(recordingUnitIdField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("isPartOf")
                        .headerKey("common.field.parents")
                        .field(isPartOfField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("contains")
                        .headerKey("common.field.children")
                        .field(containsField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("action")
                        .headerKey("recordingunit.field.actionUnit")
                        .field(actionField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .readOnly(true)
                        .required(true)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("type")
                        .headerKey("recordingunit.property.type")
                        .field(typeField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("phases")
                        .headerKey("recordingunit.field.phases")
                        .field(phasesField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                RelationColumn.builder()
                        .id("relationships")
                        .headerKey("common.label.ruRelationships")
                        .headerIcon("bi bi-diagram-2")
                        .visible(true)
                        .toggleable(true)

                        .countKey("relationships")
                        .viewIcon(BI_BI_EYE)
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(3)

                        .addEnabled(false)
                        .addIcon(BI_BI_PLUS_SQUARE)
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("specimenCreateAllowed")

                        .processExpr(THIS)
                        .updateExpr(THIS)
                        .onstartJs(PF_BUI_CONTENT_SHOW)
                        .oncompleteJs(PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP)
                        .build()
        );

        definition.addColumn(
                RelationColumn.builder()
                        .id("specimen")
                        .headerKey("common.entity.specimen")
                        .headerIcon("bi bi-bucket")
                        .visible(true)
                        .toggleable(true)

                        .countKey("specimenList")

                        .viewIcon(BI_BI_EYE)
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(2)

                        .addEnabled(false)
                        .addIcon(BI_BI_PLUS_SQUARE)
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("specimenCreateAllowed")

                        .processExpr(THIS)
                        .updateExpr(THIS)
                        .onstartJs(PF_BUI_CONTENT_SHOW)
                        .oncompleteJs(PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("spatial")
                        .headerKey("recordingunit.field.spatialUnit")
                        .field(spatialField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .readOnly(false)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("matrixColor")
                        .headerKey("recordingunit.field.matrixColor")
                        .field(matrixColor)
                        .sortable(true)
                        .filterable(true)
                        .visible(false)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("openingDate")
                        .headerKey("recordingunit.field.openingDate")
                        .field(dateField)
                        .sortable(true)
                        .filterable(true)
                        .visible(false)
                        .required(true)
                        .build()
        );
        definition.addColumn(
                FormFieldColumn.builder()
                        .id("author")
                        .headerKey("recordingunit.field.author")
                        .field(authorField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );
        definition.addColumn(
                FormFieldColumn.builder()
                        .id("contributors")
                        .headerKey("recordingunit.field.contributors")
                        .field(contributorsField)
                        .sortable(false)
                        .filterable(true)
                        .visible(false)
                        .required(false)
                        .build()
        );

        // Fields that exist on RecordingUnit.DETAILS_FORM but had no table column of their own yet:
        // configurable/toggleable, hidden from the table by default so nobody's view changes.
        TableDefinitions.addColumns(definition,
                column(systemField(ConfigurableTable.UE, "geomorphologicalCycle")).build(),
                column(systemField(ConfigurableTable.UE, "geomorphologicalAgent")).build(),
                column(systemField(ConfigurableTable.UE, "normalizedInterpretation")).build(),
                column(systemField(ConfigurableTable.UE, "erosionShape")).build(),
                column(systemField(ConfigurableTable.UE, "erosionProfile")).build(),
                column(systemField(ConfigurableTable.UE, "erosionOrientation")).build(),
                column(systemField(ConfigurableTable.UE, "description")).build(),
                column(systemField(ConfigurableTable.UE, "comments")).build(),
                column(systemField(ConfigurableTable.UE, "chronologicalPhase")).build(),
                column(systemField(ConfigurableTable.UE, "tpq")).build(),
                column(systemField(ConfigurableTable.UE, "taq")).build(),
                column(systemField(ConfigurableTable.UE, "zInf")).build(),
                column(systemField(ConfigurableTable.UE, "zSup")).build(),
                column(systemField(ConfigurableTable.UE, "closingDate")).build()
        );
    }
}
