package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.ui.table.TableDefinition;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import static fr.siamois.ui.table.definitions.TableDefinitions.IDENTIFIER;
import static fr.siamois.ui.table.definitions.TableDefinitions.addColumns;
import static fr.siamois.ui.table.definitions.TableDefinitions.column;
import static fr.siamois.ui.table.definitions.TableDefinitions.panelLinkColumn;
import static fr.siamois.ui.table.definitions.TableDefinitions.systemConcept;
import static fr.siamois.ui.table.definitions.TableDefinitions.unit;

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
        CustomFieldText identifierField = CustomFieldText.builder()
                .label(CONTAINER_FIELD_IDENTIFIER).isSystemField(true).isTextArea(false)
                .id(1L).valueBinding(IDENTIFIER).concept(systemConcept("container.identifier")).build();

        CustomFieldSelectOneFromFieldCode typeField = CustomFieldSelectOneFromFieldCode.builder()
                .label("container.field.type").isSystemField(true).id(2L)
                .valueBinding("type").fieldCode(Container.TYPE_FIELD)
                .styleClass("mr-2 container-type-chip").concept(systemConcept("container.type")).build();

        CustomFieldSelectOneSpatialUnit spatialUnitField = CustomFieldSelectOneSpatialUnit.builder()
                .label("container.field.spatialUnit").isSystemField(true)
                .id(3L).valueBinding("spatialUnit").concept(systemConcept("container.spatialUnit")).build();

        CustomFieldMeasurement lengthField = CustomFieldMeasurement.builder()
                .label("container.field.length").isSystemField(true)
                .id(4L).valueBinding("length").concept(systemConcept("container.length"))
                .unit(centimetres()).build();

        CustomFieldMeasurement widthField = CustomFieldMeasurement.builder()
                .label("container.field.width").isSystemField(true)
                .id(5L).valueBinding("width").concept(systemConcept("container.width"))
                .unit(centimetres()).build();

        CustomFieldMeasurement heightField = CustomFieldMeasurement.builder()
                .label("container.field.height").isSystemField(true)
                .id(6L).valueBinding("height").concept(systemConcept("container.height"))
                .unit(centimetres()).build();

        CustomFieldMeasurement weightField = CustomFieldMeasurement.builder()
                .label("container.field.weight").isSystemField(true)
                .id(7L).valueBinding("weight").concept(systemConcept("container.weight"))
                .unit(unit("Kilogramme", "kg", UnitDefinition.Dimension.MASS, 1000.0)).build();

        definition.setCommandLinkColumn(panelLinkColumn(CONTAINER_FIELD_IDENTIFIER, "bi bi-box-seam",
                "var(--third-main-color)", TableColumnAction.GO_TO_CONTAINER));

        addColumns(definition,
                column(identifierField).sortable(true).filterable(true).visible(true).required(true).build(),
                column(typeField).visible(true).required(true).build(),
                column(spatialUnitField).visible(true).build(),
                column(lengthField).build(),
                column(widthField).build(),
                column(heightField).build(),
                column(weightField).build());
    }

    /** The unit the dimensions of a container are given in. */
    private static UnitDefinition centimetres() {
        return unit(CENTIMETRE, "cm", UnitDefinition.Dimension.LENGTH, 0.01);
    }
}
