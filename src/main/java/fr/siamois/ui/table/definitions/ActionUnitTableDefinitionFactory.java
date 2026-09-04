package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.actionunit.form.ActionUnitForm;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.RelationColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class ActionUnitTableDefinitionFactory {

    public static final String THIS = "@this";

    private ActionUnitTableDefinitionFactory() {}

    /**
     * Applies the standard ActionUnit columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<ActionUnitDTO, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        Concept nameConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4285848")
                .build();
        CustomFieldText nameField =  CustomFieldText.builder()
                .label("common.label.name")
                .id(-151L)
                .isSystemField(true)
                .valueBinding("name")
                .concept(nameConcept)
                .build();

        // -------------------------
        // Name / identifier link col
        // -------------------------
        tableModel.getTableDefinition().setCommandLinkColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.actionunit.column.identifier")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(true)
                        .filterable(true)
                        .sortField("fullIdentifier")

                        .iconClass("bi bi-arrow-down-square")
                        .chipColor("var(--context-main-color)")
                        .valueKey("fullIdentifier")
                        .editable(true)

                        // What to do on click (Pattern A key)
                        .action(TableColumnAction.GO_TO_ACTION_UNIT)

                        // CommandLink behavior
                        .processExpr(THIS)
                        .updateExpr("@none")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();")
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
                RelationColumn.builder()
                        .id("recording")
                        .headerKey("table.spatialunit.column.recordings")
                        .headerIcon("bi bi-pencil-square")
                        .visible(true)
                        .toggleable(true)
                        .sortable(true)
                        .sortField(ActionUnitSpec.RECORDING_UNIT_COUNT_SORT)

                        .countKey("recordingUnit")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(2)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("recordingUnitCreateAllowed")

                        .processExpr(THIS)
                        .updateExpr(THIS)
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();")
                        .build()
        );

        // -------------------------
        // Documentation / administrative columns
        // Visible by default: status, OA code, main location, opening rate, periods, subjects.
        // The rest is available but hidden by default (toggleable from the column picker).
        // -------------------------
        TableDefinitions.addColumns(tableModel.getTableDefinition(),
                column("status", "actionunit.field.status", ActionUnitForm.STATUS_FIELD, true),
                column("oaCode", "actionunit.field.oaCode", ActionUnitForm.OA_CODE_FIELD, true),
                column("mainLocation", "common.label.mainLocation", ActionUnitForm.MAIN_LOCATION_FIELD, true),
                column("openingRate", "actionunit.field.openingRate", ActionUnitForm.OPENING_RATE_FIELD, true),
                column("periods", "actionunit.field.periods", ActionUnitForm.PERIODS_FIELD, true),
                column("subjects", "actionunit.field.subjects", ActionUnitForm.SUBJECTS_FIELD, true),
                column("scientificManager", "actionunit.field.scientificManager", ActionUnitForm.SCIENTIFIC_MANAGER_FIELD, true),
                column("prescriptionOrderNumber", "actionunit.field.prescriptionOrderNumber", ActionUnitForm.PRESCRIPTION_ORDER_NUMBER_FIELD, false),
                column("prescriptionOrderDate", "actionunit.field.prescriptionOrderDate", ActionUnitForm.PRESCRIPTION_ORDER_DATE_FIELD, false),
                column("hostStructure", "actionunit.field.hostStructure", ActionUnitForm.HOST_STRUCTURE_FIELD, false),
                column("developer", "actionunit.field.developer", ActionUnitForm.DEVELOPER_FIELD, false),
                column("scientificNotice", "actionunit.field.scientificNotice", ActionUnitForm.SCIENTIFIC_NOTICE_FIELD, false),
                column("comments", "common.field.comments", ActionUnitForm.COMMENTS_FIELD, false),
                column("system", "actionunit.field.system", ActionUnitForm.SYSTEM_FIELD, false),
                column("fieldStatus", "actionunit.field.fieldStatus", ActionUnitForm.FIELD_STATUS_FIELD, false),
                column("zmin", "actionunit.field.zmin", ActionUnitForm.ZMIN_FIELD, false),
                column("zmax", "actionunit.field.zmax", ActionUnitForm.ZMAX_FIELD, false),
                column("designationOrderNumber", "actionunit.field.designationOrderNumber", ActionUnitForm.DESIGNATION_ORDER_NUMBER_FIELD, false),
                column("designationOrderDate", "actionunit.field.designationOrderDate", ActionUnitForm.DESIGNATION_ORDER_DATE_FIELD, false),
                column("prescribedArea", "actionunit.field.prescribedArea", ActionUnitForm.PRESCRIBED_AREA_FIELD, false),
                column("excavatedArea", "actionunit.field.excavatedArea", ActionUnitForm.EXCAVATED_AREA_FIELD, false),
                column("accessibleArea", "actionunit.field.accessibleArea", ActionUnitForm.ACCESSIBLE_AREA_FIELD, false),
                column("developmentNature", "actionunit.field.developmentNature", ActionUnitForm.DEVELOPMENT_NATURE_FIELD, false),
                column("volumeCount", "actionunit.field.volumeCount", ActionUnitForm.VOLUME_COUNT_FIELD, false),
                column("pageCount", "actionunit.field.pageCount", ActionUnitForm.PAGE_COUNT_FIELD, false),
                column("figureCount", "actionunit.field.figureCount", ActionUnitForm.FIGURE_COUNT_FIELD, false),
                column("appendixCount", "actionunit.field.appendixCount", ActionUnitForm.APPENDIX_COUNT_FIELD, false)
        );

    }

    private static FormFieldColumn column(String id, String headerKey, CustomField field, boolean visible) {
        return FormFieldColumn.builder()
                .id(id)
                .headerKey(headerKey)
                .field(field)
                .sortable(true)
                .filterable(true)
                .visible(visible)
                .toggleable(true)
                .build();
    }
}
