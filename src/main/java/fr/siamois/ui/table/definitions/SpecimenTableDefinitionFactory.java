package fr.siamois.ui.table.definitions;


import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.table.CommandLinkColumn;
import fr.siamois.ui.table.EntityTableViewModel;
import fr.siamois.ui.table.FormFieldColumn;
import fr.siamois.ui.table.TableColumnAction;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class SpecimenTableDefinitionFactory {

    private SpecimenTableDefinitionFactory() {}

    /**
     * Applies the standard Specimen columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<Specimen, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        Concept idConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286250")
                .build();
        Concept typeConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286248")
                .build();

        CustomFieldText idField =  CustomFieldText.builder()
                .label("recordingunit.field.identifier")
                .isSystemField(true)
                .isTextArea(false)
                .id(1L)
                .valueBinding("fullIdentifier")
                .concept(idConcept)
                .build();


        CustomFieldSelectOneFromFieldCode catField =  CustomFieldSelectOneFromFieldCode.builder()
                .label("recordingunit.property.type")
                .isSystemField(true)
                .id(2L)
                .valueBinding("category")
                .concept(typeConcept)
                .fieldCode("SIAS.CAT")
                .styleClass("mr-2 recording-unit-type-chip")
                .build();



        tableModel.getTableDefinition().addColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.recordingunit.column.identifier")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(false)
                        .filterable(false)
                        .sortField("full_identifier")

                        // What to display inside <h:outputText>
                        .valueKey("fullIdentifier")

                        // What to do on click (Pattern A key)
                        .action(TableColumnAction.GO_TO_SPECIMEN)

                        // CommandLink behavior
                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("identifier")
                        .headerKey("recordingunit.field.identifier")
                        .field(idField)
                        .sortable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("type")
                        .headerKey("recordingunit.property.type")
                        .field(catField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(true)
                        .build()
        );

    }
}
