package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.TableColumnAction;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

public class ContainerTableDefinitionFactory {

    public static final String THIS = "@this";
    public static final String IDENTIFIER = "identifier";

    private ContainerTableDefinitionFactory() {
    }

    public static void applyTo(EntityTableViewModel<ContainerDTO, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        Concept nameConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4285848")
                .build();
        CustomFieldText identifierField = CustomFieldText.builder()
                .label("common.label.identifier")
                .id(2L)
                .isSystemField(true)
                .valueBinding(IDENTIFIER)
                .concept(nameConcept)
                .build();

        // -------------------------
        // Name / identifier link col
        // -------------------------
        tableModel.getTableDefinition().setCommandLinkColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.spatialunit.column.name")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(false)
                        .filterable(false)
                        .sortField("name")

                        .iconClass("bi bi-arrow-down-square")
                        .chipColor("var(--context-main-color)")
                        .valueKey("name")

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
                        .id(IDENTIFIER)
                        .headerKey("container.field.identifier")
                        .field(identifierField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );


    }
}
