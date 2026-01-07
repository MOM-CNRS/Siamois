package fr.siamois.ui.table.definitions;


import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.ui.table.CommandLinkColumn;
import fr.siamois.ui.table.EntityTableViewModel;
import fr.siamois.ui.table.TableColumnAction;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class SpecimenTableDefinitionFactory {

    private SpecimenTableDefinitionFactory() {}

    /**
     * Applies the standard Specimen columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<Specimen, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        tableModel.getTableDefinition().addColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.recordingunit.column.identifier")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(true)
                        .sortField("full_identifier")

                        // What to display inside <h:outputText>
                        .valueKey("fullIdentifier")

                        // What to do on click (Pattern A key)
                        .action(TableColumnAction.GO_TO_SPECIMEN)

                        // CommandLink behavior
                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

    }
}
