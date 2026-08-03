package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.phase.Phase;
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
        CustomFieldText identifierField = CustomFieldText.builder()
                .label(PHASE_FIELD_IDENTIFIER).isSystemField(true).isTextArea(false)
                .id(1L).valueBinding(IDENTIFIER).concept(systemConcept("phase.identifier")).build();

        CustomFieldSelectOneFromFieldCode typeField = CustomFieldSelectOneFromFieldCode.builder()
                .label("phase.field.type").isSystemField(true).id(2L)
                .valueBinding("type").fieldCode(Phase.TYPE_FIELD)
                .styleClass("mr-2 phase-type-chip").concept(systemConcept("phase.type")).build();

        CustomFieldText titleField = CustomFieldText.builder()
                .label("phase.field.title").isSystemField(true).isTextArea(false)
                .id(3L).valueBinding("title").concept(systemConcept("phase.title")).build();

        CustomFieldInteger orderNumberField = CustomFieldInteger.builder()
                .label("phase.field.orderNumber").isSystemField(true)
                .id(5L).valueBinding("orderNumber").concept(systemConcept("phase.orderNumber"))
                .minValue(0).maxValue(Integer.MAX_VALUE).build();

        CustomFieldInteger lowerBoundField = CustomFieldInteger.builder()
                .label("phase.field.lowerBound").isSystemField(true)
                .id(6L).valueBinding("lowerBound").concept(systemConcept("phase.lowerBound"))
                .minValue(Integer.MIN_VALUE).maxValue(Integer.MAX_VALUE).build();

        CustomFieldInteger upperBoundField = CustomFieldInteger.builder()
                .label("phase.field.upperBound").isSystemField(true)
                .id(7L).valueBinding("upperBound").concept(systemConcept("phase.upperBound"))
                .minValue(Integer.MIN_VALUE).maxValue(Integer.MAX_VALUE).build();

        CustomFieldSelectMultipleFromFieldCode periodsField = CustomFieldSelectMultipleFromFieldCode.builder()
                .label("phase.field.periods").isSystemField(true).id(8L)
                .valueBinding("periods").fieldCode(Phase.PERIOD_FIELD).concept(systemConcept("phase.periods")).build();

        CustomFieldSelectMultipleFromFieldCode keywordsField = CustomFieldSelectMultipleFromFieldCode.builder()
                .label("phase.field.keywords").isSystemField(true).id(9L)
                .valueBinding("keywords").fieldCode(Phase.KEYWORD_FIELD).concept(systemConcept("phase.keywords")).build();

        CustomFieldText descriptionField = CustomFieldText.builder()
                .label("phase.field.description").isSystemField(true).isTextArea(true)
                .id(4L).valueBinding("description").concept(systemConcept("phase.description")).build();

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
                column(descriptionField).build());
    }
}
