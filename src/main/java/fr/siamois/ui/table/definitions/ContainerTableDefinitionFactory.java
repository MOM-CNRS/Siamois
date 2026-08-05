package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import static fr.siamois.ui.table.definitions.TableDefinitions.IDENTIFIER;
import static fr.siamois.ui.table.definitions.TableDefinitions.addColumns;
import static fr.siamois.ui.table.definitions.TableDefinitions.column;
import static fr.siamois.ui.table.definitions.TableDefinitions.panelLinkColumn;
import static fr.siamois.ui.table.definitions.TableDefinitions.systemField;
import static fr.siamois.ui.table.definitions.TableDefinitions.*;

public class ContainerTableDefinitionFactory {

    public static final String CONTAINER_FIELD_IDENTIFIER = "container.field.identifier";
    public static final String CENTIMETRE = "Centimètre";

    private ContainerTableDefinitionFactory() {}

    public static void applyTo(EntityTableViewModel<ContainerDTO, ?> tableModel) {
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
        CustomField identifierField = systemField(ConfigurableTable.CONTENANT, IDENTIFIER);
        CustomField typeField = systemField(ConfigurableTable.CONTENANT, "type");
        CustomField spatialUnitField = systemField(ConfigurableTable.CONTENANT, "spatialUnit");
        CustomField actionUnitField = systemField(ConfigurableTable.CONTENANT, "actionUnit");
        CustomField lengthField = systemField(ConfigurableTable.CONTENANT, "length");
        CustomField widthField = systemField(ConfigurableTable.CONTENANT, "width");
        CustomField heightField = systemField(ConfigurableTable.CONTENANT, "height");
        CustomField weightField = systemField(ConfigurableTable.CONTENANT, "weight");

        definition.setCommandLinkColumn(panelLinkColumn(CONTAINER_FIELD_IDENTIFIER, "bi bi-box-seam",
                "var(--third-main-color)", TableColumnAction.GO_TO_CONTAINER));

        addColumns(definition,
                column(identifierField).sortable(true).filterable(true).visible(true).required(true).build(),
                column(typeField).visible(true).required(true).build(),
                column(spatialUnitField).visible(true).build(),
                column(actionUnitField).visible(true).build(),
                column(lengthField).build(),
                column(widthField).build(),
                column(heightField).build(),
                column(weightField).build());
    }

}
