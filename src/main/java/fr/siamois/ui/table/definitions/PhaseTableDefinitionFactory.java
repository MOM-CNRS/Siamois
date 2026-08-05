package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import static fr.siamois.ui.table.definitions.TableDefinitions.*;

public class PhaseTableDefinitionFactory {

    public static final String PHASE_FIELD_IDENTIFIER = "phase.field.identifier";

    private PhaseTableDefinitionFactory() {}

    public static void applyTo(EntityTableViewModel<PhaseDTO, ?> tableModel) {
        if (tableModel == null) {
            return;
        }
        applyTo(tableModel.getTableDefinition());
    }

    /**
     * The columns of the table on their own, with no view model to apply them to: the field
     * configuration screen reads a table's system fields from here, since they are defined in this
     * factory rather than in the database.
     *
     * @return a fresh definition carrying the table's standard columns
     */
    public static TableDefinition definition() {
        TableDefinition definition = new TableDefinition();
        applyTo(definition);
        return definition;
    }

    private static void applyTo(TableDefinition definition) {
        CustomField identifierField = systemField(ConfigurableTable.PHASE, IDENTIFIER);
        CustomField typeField = systemField(ConfigurableTable.PHASE, "type");
        CustomField titleField = systemField(ConfigurableTable.PHASE, "title");
        CustomField orderNumberField = systemField(ConfigurableTable.PHASE, "orderNumber");
        CustomField lowerBoundField = systemField(ConfigurableTable.PHASE, "lowerBound");
        CustomField upperBoundField = systemField(ConfigurableTable.PHASE, "upperBound");
        CustomField periodsField = systemField(ConfigurableTable.PHASE, "periods");
        CustomField keywordsField = systemField(ConfigurableTable.PHASE, "keywords");
        CustomField descriptionField = systemField(ConfigurableTable.PHASE, "description");
        CustomField actionUnitField = systemField(ConfigurableTable.PHASE, "actionUnit");

        definition.setCommandLinkColumn(panelLinkColumn(PHASE_FIELD_IDENTIFIER, "bi bi-layers",
                "var(--ground-main-color)", TableColumnAction.GO_TO_PHASE));

        addColumns(definition,
                column(identifierField).sortable(true).filterable(true).visible(true).required(true).build(),
                column(typeField).visible(true).required(true).build(),
                column(titleField).visible(true).build(),
                column(orderNumberField).sortable(true).visible(true).build(),
                column(lowerBoundField).build(),
                column(upperBoundField).build(),
                column(periodsField).build(),
                column(keywordsField).build(),
                column(descriptionField).build(),
                column(actionUnitField).visible(true).required(true).build());
    }
}
