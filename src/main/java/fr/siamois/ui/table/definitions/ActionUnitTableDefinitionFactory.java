package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.table.*;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;


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

        Concept nameConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4285848")
                .build();
        CustomFieldText nameField =  CustomFieldText.builder()
                .label("common.label.name")
                .id(2L)
                .isSystemField(true)
                .valueBinding("name")
                .concept(nameConcept)
                .build();

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
                FormFieldColumn.builder()
                        .id("name")
                        .headerKey("spatialunit.field.name")
                        .field(nameField)
                        .sortable(true)
                        .visible(true)
                        .required(true)
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
