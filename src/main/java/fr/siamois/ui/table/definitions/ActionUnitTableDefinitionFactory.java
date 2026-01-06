package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.ui.table.CommandLinkColumn;
import fr.siamois.ui.table.EntityTableViewModel;
import fr.siamois.ui.table.RelationColumn;
import fr.siamois.ui.table.TableColumnAction;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class ActionUnitTableDefinitionFactory {

    private ActionUnitTableDefinitionFactory() {}

    /**
     * Applies the standard ActionUnit columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<ActionUnit, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        // -------------------------
        // Name / identifier link col
        // -------------------------
        tableModel.getTableDefinition().addColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.spatialunit.column.name")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(true)
                        .sortField("name")

                        // What to display inside <h:outputText>
                        .valueKey("identifier")

                        // What to do on click (Pattern A key)
                        .action(TableColumnAction.GO_TO_ACTION_UNIT)

                        // CommandLink behavior
                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("recording")
                        .headerKey("table.spatialunit.column.recordings")
                        .headerIcon("bi bi-pencil-square")
                        .visible(true)
                        .toggleable(true)

                        .countKey("recordingUnit")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(2)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("recordingUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );



    }
}
