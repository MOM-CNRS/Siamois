package fr.siamois.ui.table.definitions;

import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.RelationColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

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

        definition.setCommandLinkColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.recordingunit.column.identifier")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(true)
                        .filterable(true)
                        .sortField("fullIdentifier")

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
        // isPartOf / contains / action / type / phases — RecordingUnitTableColumnDefaults' own
        // order up to (not including) the relationships/specimen count columns below, which have
        // no CustomField and so stay hand-built.
        addDefaultColumns(definition, "isPartOf", "contains", "action", "type", "phases");

        definition.addColumn(
                RelationColumn.builder()
                        .id("relationships")
                        .headerKey("common.label.ruRelationships")
                        .headerIcon("bi bi-diagram-2")
                        .visible(true)
                        .toggleable(true)
                        .sortable(true)
                        .sortField(RecordingUnitSpec.RELATIONSHIP_COUNT_SORT)

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
                        .sortable(true)
                        .sortField(RecordingUnitSpec.SPECIMEN_COUNT_SORT)

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

        // spatial through closingDate — RecordingUnitTableColumnDefaults' own order for the rest
        // of the table, all field-catalog-backed columns.
        addDefaultColumns(definition, "spatial", "matrixColor", "openingDate", "author", "contributors",
                "geomorphologicalCycle", "geomorphologicalAgent", "normalizedInterpretation", "tpq", "taq",
                "erosionShape", "erosionProfile", "erosionOrientation", "description", "comments",
                "chronologicalPhase", "zInf", "zSup", "closingDate");
    }

    /**
     * Adds the given {@link RecordingUnitTableColumnDefaults} entries, by columnId, in the order
     * requested — lets {@link #applyTo(TableDefinition)} interleave them around the hand-built
     * relationships/specimen count columns while still reading every field-catalog column's shape
     * (visible/sortable/filterable/sortField/required/readOnly) from the one shared source of
     * truth the REST catalog endpoint also reads.
     */
    private static void addDefaultColumns(TableDefinition definition, String... columnIds) {
        Map<String, RecordingUnitTableColumnDefaults.ColumnDefault> byId = RecordingUnitTableColumnDefaults.columns()
                .stream()
                .collect(Collectors.toMap(RecordingUnitTableColumnDefaults.ColumnDefault::columnId, c -> c));
        for (String columnId : columnIds) {
            RecordingUnitTableColumnDefaults.ColumnDefault c = byId.get(columnId);
            if (c == null) {
                throw new NoSuchElementException("No RecordingUnitTableColumnDefaults entry for '" + columnId + "'");
            }
            definition.addColumn(
                    FormFieldColumn.builder()
                            .id(c.columnId())
                            .headerKey(c.headerKey())
                            .field(c.field())
                            .sortable(c.sortable())
                            .sortField(c.sortField())
                            .filterable(c.filterable())
                            .visible(c.visible())
                            .toggleable(true)
                            .required(c.required())
                            .readOnly(c.readOnly())
                            .build()
            );
        }
    }
}
