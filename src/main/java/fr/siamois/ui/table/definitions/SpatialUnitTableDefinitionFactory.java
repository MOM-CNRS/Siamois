package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.ui.table.CommandLinkColumn;
import fr.siamois.ui.table.EntityTableViewModel;
import fr.siamois.ui.table.RelationColumn;
import fr.siamois.ui.table.TableColumnAction;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class SpatialUnitTableDefinitionFactory {

    private SpatialUnitTableDefinitionFactory() {}

    /**
     * Applies the standard SpatialUnit columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<SpatialUnit, ?> tableModel) {
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

                        .toggleable(false)
                        .sortable(true)
                        .sortField("name")

                        .valueKey("name")
                        .action(TableColumnAction.GO_TO_SPATIAL_UNIT)

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

        // --------
        // Parents
        // --------
        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("parents")
                        .headerKey("table.spatialunit.column.parents")
                        .headerIcon("bi bi-pencil-square")
                        .visible(true)
                        .toggleable(true)

                        .countKey("parents")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("spatialUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

        // ---------
        // Children
        // ---------
        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("children")
                        .headerKey("table.spatialunit.column.children")
                        .headerIcon("bi bi-pencil-square")
                        .visible(true)
                        .toggleable(true)

                        .countKey("children")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("spatialUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

        // -------
        // Actions
        // -------
        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("action")
                        .headerKey("table.spatialunit.column.actions")
                        .headerIcon("bi bi-arrow-down-square")
                        .visible(true)
                        .toggleable(true)

                        .countKey("actions")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("actionUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

    }
}
