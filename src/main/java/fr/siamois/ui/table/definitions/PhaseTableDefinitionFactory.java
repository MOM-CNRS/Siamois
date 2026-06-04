package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

public class PhaseTableDefinitionFactory {

    private static final String THIS = "@this";

    private PhaseTableDefinitionFactory() {}

    public static void applyTo(EntityTableViewModel<PhaseDTO, ?> tableModel) {
        if (tableModel == null) return;

        Concept identifierConcept = new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("phase.identifier").build();
        Concept typeConcept = new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("phase.type").build();
        Concept titleConcept = new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("phase.title").build();
        Concept orderNumberConcept = new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("phase.orderNumber").build();
        Concept lowerBoundConcept = new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("phase.lowerBound").build();
        Concept upperBoundConcept = new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("phase.upperBound").build();
        Concept periodsConcept = new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("phase.periods").build();
        Concept keywordsConcept = new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("phase.keywords").build();
        Concept descriptionConcept = new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("phase.description").build();

        CustomFieldText identifierField = CustomFieldText.builder()
                .label("phase.field.identifier").isSystemField(true).isTextArea(false)
                .id(1L).valueBinding("identifier").concept(identifierConcept).build();

        CustomFieldSelectOneFromFieldCode typeField = CustomFieldSelectOneFromFieldCode.builder()
                .label("phase.field.type").isSystemField(true).id(2L)
                .valueBinding("type").fieldCode(Phase.TYPE_FIELD)
                .styleClass("mr-2 phase-type-chip").concept(typeConcept).build();

        CustomFieldText titleField = CustomFieldText.builder()
                .label("phase.field.title").isSystemField(true).isTextArea(false)
                .id(3L).valueBinding("title").concept(titleConcept).build();

        CustomFieldInteger orderNumberField = CustomFieldInteger.builder()
                .label("phase.field.orderNumber").isSystemField(true)
                .id(5L).valueBinding("orderNumber").concept(orderNumberConcept).build();

        CustomFieldInteger lowerBoundField = CustomFieldInteger.builder()
                .label("phase.field.lowerBound").isSystemField(true)
                .id(6L).valueBinding("lowerBound").concept(lowerBoundConcept).build();

        CustomFieldInteger upperBoundField = CustomFieldInteger.builder()
                .label("phase.field.upperBound").isSystemField(true)
                .id(7L).valueBinding("upperBound").concept(upperBoundConcept).build();

        CustomFieldSelectMultipleFromFieldCode periodsField = CustomFieldSelectMultipleFromFieldCode.builder()
                .label("phase.field.periods").isSystemField(true).id(8L)
                .valueBinding("periods").fieldCode(Phase.PERIOD_FIELD).concept(periodsConcept).build();

        CustomFieldSelectMultipleFromFieldCode keywordsField = CustomFieldSelectMultipleFromFieldCode.builder()
                .label("phase.field.keywords").isSystemField(true).id(9L)
                .valueBinding("keywords").fieldCode(Phase.KEYWORD_FIELD).concept(keywordsConcept).build();

        CustomFieldText descriptionField = CustomFieldText.builder()
                .label("phase.field.description").isSystemField(true).isTextArea(true)
                .id(4L).valueBinding("description").concept(descriptionConcept).build();

        // CommandLink column
        tableModel.getTableDefinition().setCommandLinkColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("phase.field.identifier")
                        .visible(true).toggleable(false).sortable(false).filterable(false)
                        .sortField("identifier")
                        .iconClass("bi bi-layers")
                        .chipColor("var(--ground-main-color)")
                        .valueKey("identifier")
                        .action(TableColumnAction.GO_TO_PHASE)
                        .processExpr(THIS)
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();")
                        .build()
        );

        // Visible columns
        tableModel.getTableDefinition().addColumn(FormFieldColumn.builder()
                .id("identifier").headerKey("phase.field.identifier").field(identifierField)
                .sortable(true).filterable(true).visible(true).required(true).build());

        tableModel.getTableDefinition().addColumn(FormFieldColumn.builder()
                .id("type").headerKey("phase.field.type").field(typeField)
                .sortable(false).filterable(false).visible(true).required(true).build());

        tableModel.getTableDefinition().addColumn(FormFieldColumn.builder()
                .id("title").headerKey("phase.field.title").field(titleField)
                .sortable(false).filterable(false).visible(true).required(false).build());

        tableModel.getTableDefinition().addColumn(FormFieldColumn.builder()
                .id("orderNumber").headerKey("phase.field.orderNumber").field(orderNumberField)
                .sortable(true).filterable(false).visible(true).required(false).build());

        // Hidden columns
        tableModel.getTableDefinition().addColumn(FormFieldColumn.builder()
                .id("lowerBound").headerKey("phase.field.lowerBound").field(lowerBoundField)
                .sortable(false).filterable(false).visible(false).required(false).build());

        tableModel.getTableDefinition().addColumn(FormFieldColumn.builder()
                .id("upperBound").headerKey("phase.field.upperBound").field(upperBoundField)
                .sortable(false).filterable(false).visible(false).required(false).build());

        tableModel.getTableDefinition().addColumn(FormFieldColumn.builder()
                .id("periods").headerKey("phase.field.periods").field(periodsField)
                .sortable(false).filterable(false).visible(false).required(false).build());

        tableModel.getTableDefinition().addColumn(FormFieldColumn.builder()
                .id("keywords").headerKey("phase.field.keywords").field(keywordsField)
                .sortable(false).filterable(false).visible(false).required(false).build());

        tableModel.getTableDefinition().addColumn(FormFieldColumn.builder()
                .id("description").headerKey("phase.field.description").field(descriptionField)
                .sortable(false).filterable(false).visible(false).required(false).build());
    }
}
