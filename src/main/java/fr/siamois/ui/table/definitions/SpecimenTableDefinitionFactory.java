package fr.siamois.ui.table.definitions;


import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import static fr.siamois.ui.table.definitions.TableDefinitions.column;
import static fr.siamois.ui.table.definitions.TableDefinitions.systemField;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class SpecimenTableDefinitionFactory {

    private static final String THIS = "@this";
    private static final String PF_BUI_CONTENT_SHOW = "PF('buiContent').show()";
    private static final String PF_BUI_CONTENT_HIDE = "PF('buiContent').hide();";

    private SpecimenTableDefinitionFactory() {}

    /**
     * Applies the standard Specimen columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<SpecimenDTO, ?> tableModel) {
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

        CustomField idField = systemField(ConfigurableTable.MOBILIER, "fullIdentifier");
        CustomField categoryField = systemField(ConfigurableTable.MOBILIER, "category");
        CustomField recordingUnitField = systemField(ConfigurableTable.MOBILIER, "recordingUnit");
        CustomField isPartOfField = systemField(ConfigurableTable.MOBILIER, "parents");
        CustomField containsField = systemField(ConfigurableTable.MOBILIER, "children");
        CustomField otherIdField = systemField(ConfigurableTable.MOBILIER, "otherIdentifier");
        CustomField isolationNumberField = systemField(ConfigurableTable.MOBILIER, "isolationNumber");
        CustomField authorsField = systemField(ConfigurableTable.MOBILIER, "authors");
        CustomField collectorsField = systemField(ConfigurableTable.MOBILIER, "collectors");
        CustomField collectionDateField = systemField(ConfigurableTable.MOBILIER, "collectionDate");
        CustomField materialField = systemField(ConfigurableTable.MOBILIER, "material");
        CustomField materialClassField = systemField(ConfigurableTable.MOBILIER, "materialClass");
        CustomField normalizedInterpretationField = systemField(ConfigurableTable.MOBILIER, "normalizedInterpretation");
        CustomField chronologicalAttributionField = systemField(ConfigurableTable.MOBILIER, "chronologicalAttribution");
        CustomField numberOfElementField = systemField(ConfigurableTable.MOBILIER, "numberOfElements");
        CustomField phasesField = systemField(ConfigurableTable.MOBILIER, "phases");
        CustomField actionUnitField = systemField(ConfigurableTable.MOBILIER, "actionUnit");

        // --- CommandLink column (non-toggleable identifier chip) ---
        definition.setCommandLinkColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.recordingunit.column.identifier")
                        .visible(true)
                        .toggleable(false)
                        .sortable(false)
                        .filterable(false)
                        .sortField("full_identifier")
                        .iconClass("bi bi-bucket")
                        .chipColor("var(--ground-main-color)")
                        .valueKey("fullIdentifier")
                        .action(TableColumnAction.GO_TO_SPECIMEN)
                        .processExpr(THIS)
                        .updateExpr("flow")
                        .onstartJs(PF_BUI_CONTENT_SHOW)
                        .oncompleteJs(PF_BUI_CONTENT_HIDE)
                        .build()
        );

        // --- Visible columns ---
        definition.addColumn(
                FormFieldColumn.builder()
                        .id("identifier")
                        .headerKey("recordingunit.field.identifier")
                        .field(idField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("category")
                        .headerKey("specimen.field.category")
                        .field(categoryField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("material")
                        .headerKey("specimen.field.material")
                        .field(materialField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("materialClass")
                        .headerKey("specimen.field.materialClass")
                        .field(materialClassField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("normalizedInterpretation")
                        .headerKey("specimen.field.normalizedInterpretation")
                        .field(normalizedInterpretationField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("recordingUnit")
                        .headerKey("specimen.field.recordingUnit")
                        .field(recordingUnitField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("isPartOf")
                        .headerKey("specimen.field.isPartOf")
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
                        .headerKey("specimen.field.contains")
                        .field(containsField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("authors")
                        .headerKey("specimen.field.authors")
                        .field(authorsField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        // --- Hidden/toggleable columns ---
        definition.addColumn(
                FormFieldColumn.builder()
                        .id("chronologicalAttribution")
                        .headerKey("specimen.field.chronologicalAttribution")
                        .field(chronologicalAttributionField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("collectionDate")
                        .headerKey("specimen.field.collectionDate")
                        .field(collectionDateField)
                        .sortable(true)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("collectors")
                        .headerKey("specimen.field.collectors")
                        .field(collectorsField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("numberOfElements")
                        .headerKey("specimen.field.numberOfElement")
                        .field(numberOfElementField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("otherIdentifier")
                        .headerKey("recordingunit.field.otherIdentifier")
                        .field(otherIdField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("isolationNumber")
                        .headerKey("recordingunit.field.isolationIdentifier")
                        .field(isolationNumberField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("phases")
                        .headerKey("specimen.field.phases")
                        .field(phasesField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        definition.addColumn(
                FormFieldColumn.builder()
                        .id("actionUnit")
                        .headerKey("specimen.field.actionUnit")
                        .field(actionUnitField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        // Fields that exist on Specimen.DETAILS_FORM but had no table column of their own yet:
        // configurable/toggleable, hidden from the table by default so nobody's view changes.
        TableDefinitions.addColumns(definition,
                column(systemField(ConfigurableTable.MOBILIER, "description")).build(),
                column(systemField(ConfigurableTable.MOBILIER, "comments")).build(),
                column(systemField(ConfigurableTable.MOBILIER, "taq")).build(),
                column(systemField(ConfigurableTable.MOBILIER, "tpq")).build(),
                column(systemField(ConfigurableTable.MOBILIER, "weight")).build(),
                column(systemField(ConfigurableTable.MOBILIER, "containers")).build()
        );
    }
}
