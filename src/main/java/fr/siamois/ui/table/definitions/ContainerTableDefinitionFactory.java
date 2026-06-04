package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

public class ContainerTableDefinitionFactory {

    public static final String THIS = "@this";

    private ContainerTableDefinitionFactory() {}

    public static void applyTo(EntityTableViewModel<ContainerDTO, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        Concept identifierConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("container.identifier")
                .build();
        Concept typeConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("container.type")
                .build();
        Concept spatialUnitConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("container.spatialUnit")
                .build();
        Concept lengthConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("container.length")
                .build();
        Concept widthConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("container.width")
                .build();
        Concept heightConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("container.height")
                .build();
        Concept weightConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("container.weight")
                .build();

        CustomFieldText identifierField = CustomFieldText.builder()
                .label("container.field.identifier")
                .isSystemField(true)
                .isTextArea(false)
                .id(1L)
                .valueBinding("identifier")
                .concept(identifierConcept)
                .build();

        CustomFieldSelectOneFromFieldCode typeField = CustomFieldSelectOneFromFieldCode.builder()
                .label("container.field.type")
                .isSystemField(true)
                .id(2L)
                .valueBinding("type")
                .fieldCode(Container.TYPE_FIELD)
                .styleClass("mr-2 container-type-chip")
                .concept(typeConcept)
                .build();

        CustomFieldSelectOneSpatialUnit spatialUnitField = CustomFieldSelectOneSpatialUnit.builder()
                .label("container.field.spatialUnit")
                .isSystemField(true)
                .id(3L)
                .valueBinding("spatialUnit")
                .concept(spatialUnitConcept)
                .build();

        CustomFieldMeasurement lengthField = CustomFieldMeasurement.builder()
                .label("container.field.length")
                .isSystemField(true)
                .id(4L)
                .valueBinding("length")
                .unit(new UnitDefinition(null, null, "Centimètre", "cm", UnitDefinition.Dimension.LENGTH, 0.01, false))
                .concept(lengthConcept)
                .build();

        CustomFieldMeasurement widthField = CustomFieldMeasurement.builder()
                .label("container.field.width")
                .isSystemField(true)
                .id(5L)
                .valueBinding("width")
                .unit(new UnitDefinition(null, null, "Centimètre", "cm", UnitDefinition.Dimension.LENGTH, 0.01, false))
                .concept(widthConcept)
                .build();

        CustomFieldMeasurement heightField = CustomFieldMeasurement.builder()
                .label("container.field.height")
                .isSystemField(true)
                .id(6L)
                .valueBinding("height")
                .unit(new UnitDefinition(null, null, "Centimètre", "cm", UnitDefinition.Dimension.LENGTH, 0.01, false))
                .concept(heightConcept)
                .build();

        CustomFieldMeasurement weightField = CustomFieldMeasurement.builder()
                .label("container.field.weight")
                .isSystemField(true)
                .id(7L)
                .valueBinding("weight")
                .unit(new UnitDefinition(null, null, "Kilogramme", "kg", UnitDefinition.Dimension.MASS, 1000.0, false))
                .concept(weightConcept)
                .build();

        // --- CommandLink column ---
        tableModel.getTableDefinition().setCommandLinkColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("container.field.identifier")
                        .visible(true)
                        .toggleable(false)
                        .sortable(false)
                        .filterable(false)
                        .sortField("identifier")
                        .iconClass("bi bi-box-seam")
                        .chipColor("var(--third-main-color)")
                        .valueKey("identifier")
                        .action(TableColumnAction.GO_TO_CONTAINER)
                        .processExpr(THIS)
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();")
                        .build()
        );

        // --- Visible columns ---
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("identifier")
                        .headerKey("container.field.identifier")
                        .field(identifierField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("type")
                        .headerKey("container.field.type")
                        .field(typeField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(true)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("spatialUnit")
                        .headerKey("container.field.spatialUnit")
                        .field(spatialUnitField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        // --- Hidden/toggleable columns ---
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("length")
                        .headerKey("container.field.length")
                        .field(lengthField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("width")
                        .headerKey("container.field.width")
                        .field(widthField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("height")
                        .headerKey("container.field.height")
                        .field(heightField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("weight")
                        .headerKey("container.field.weight")
                        .field(weightField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );
    }
}