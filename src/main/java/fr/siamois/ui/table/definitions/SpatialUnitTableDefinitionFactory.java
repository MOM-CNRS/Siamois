package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.table.*;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class SpatialUnitTableDefinitionFactory {

    public static final String BI_BI_PLUS_SQUARE = "bi bi-plus-square";
    public static final String BI_BI_EYE = "bi bi-eye";
    public static final String THIS = "@this";
    public static final String PF_BUI_CONTENT_SHOW = "PF('buiContent').show()";
    public static final String PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP = "PF('buiContent').hide();handleScrollToTop();";

    private SpatialUnitTableDefinitionFactory() {
    }

    /**
     * Applies the standard SpatialUnit columns + toolbar create config to the given tableModel.
     * <p>
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<SpatialUnit, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        // uni category
        final Concept spatialUnitTypeConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4282365")
                .build();
        // unit name
        final Concept nameConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4285848")
                .build();



        // --------------- Fields
        CustomFieldSelectOneFromFieldCode spatialUnitTypeField = CustomFieldSelectOneFromFieldCode.builder()
                .label("specimen.field.category")
                .id(1L)
                .isSystemField(true)
                .valueBinding("category")
                .styleClass("mr-2 spatial-unit-type-chip")
                .iconClass("bi bi-geo-alt")
                .fieldCode(SpatialUnit.CATEGORY_FIELD_CODE)
                .concept(spatialUnitTypeConcept)
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

                        .toggleable(false)
                        .sortable(false)
                        .filterable(false)
                        .sortField("name")

                        .valueKey("name")
                        .action(TableColumnAction.GO_TO_SPATIAL_UNIT)

                        .processExpr(THIS)
                        .updateExpr("flow")
                        .onstartJs(PF_BUI_CONTENT_SHOW)
                        .oncompleteJs(PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("name")
                        .headerKey("spatialunit.field.name")
                        .field(nameField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("type")
                        .headerKey("spatialunit.field.type")
                        .field(spatialUnitTypeField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(true)
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

                        .viewIcon(BI_BI_EYE)
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(3)

                        .addEnabled(false)
                        .addIcon(BI_BI_PLUS_SQUARE)
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("actionUnitCreateAllowed")

                        .processExpr(THIS)
                        .updateExpr("flow")
                        .onstartJs(PF_BUI_CONTENT_SHOW)
                        .oncompleteJs(PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP)
                        .build()
        );

    }
}
