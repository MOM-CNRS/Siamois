package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.table.*;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class RecordingUnitTableDefinitionFactory {

    public static final String THIS = "@this";
    public static final String PF_BUI_CONTENT_SHOW = "PF('buiContent').show()";
    public static final String PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP = "PF('buiContent').hide();handleScrollToTop();";
    public static final String BI_BI_EYE = "bi bi-eye";
    public static final String BI_BI_PLUS_SQUARE = "bi bi-plus-square";

    private RecordingUnitTableDefinitionFactory() {}

    /**
     * Applies the standard RecordingUnit columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<RecordingUnitDTO, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        Concept typeConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4287605")
                .build();
        Concept openingdateConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286198")
                .build();
        Concept authorConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286195")
                .build();
        Concept contributorsConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286195")
                .build();
        Concept actionConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286244")
                .build();
        Concept spatialConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286245")
                .build();
        Concept recordingUnitIdConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286193")
                .build();
        Concept matrixColorConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("Couleur de la matrice")
                .build();

        CustomFieldText recordingUnitIdField =  CustomFieldText.builder()
                .label("recordingunit.field.identifier")
                .isSystemField(true)
                .isTextArea(false)
                .id(8L)
                .valueBinding("fullIdentifier")
                .concept(recordingUnitIdConcept)
                .build();


        CustomFieldSelectOneFromFieldCode typeField =  CustomFieldSelectOneFromFieldCode.builder()
                .label("recordingunit.property.type")
                .isSystemField(true)
                .id(1L)
                .valueBinding("type")
                .concept(typeConcept)
                .fieldCode("SIARU.TYPE")
                .styleClass("mr-2 recording-unit-type-chip")
                .build();

        CustomFieldText matrixColor =  CustomFieldText.builder()
                .label("recordingunit.field.matrixColor")
                .isSystemField(true)
                .isTextArea(false)
                .id(9L)
                .valueBinding("matrixColor")
                .concept(matrixColorConcept)
                .build();

        CustomFieldDateTime dateField =  CustomFieldDateTime.builder()
                .label("recordingunit.field.openingDate")
                .isSystemField(true)
                .id(2L)
                .valueBinding("openingDate")
                .concept(openingdateConcept)
                .showTime(false)
                .build();
        CustomFieldSelectOnePerson authorField =  CustomFieldSelectOnePerson.builder()
                .label("recordingunit.field.author")
                .isSystemField(true)
                .id(3L)
                .valueBinding("author")
                .concept(authorConcept)
                .build();
        CustomFieldSelectMultiplePerson contributorsField =  CustomFieldSelectMultiplePerson.builder()
                .label("recordingunit.field.contributors")
                .isSystemField(true)
                .id(4L)
                .valueBinding("contributors")
                .concept(contributorsConcept)
                .build();
        CustomFieldSelectOneActionUnit actionField = CustomFieldSelectOneActionUnit.builder()
                .label("recordingunit.field.actionUnit")
                .isSystemField(true)
                .id(5L)
                .valueBinding("actionUnit")
                .concept(actionConcept)
                .build();
        CustomFieldSelectOneSpatialUnit spatialField = CustomFieldSelectOneSpatialUnit.builder()
                .label("recordingunit.field.spatialUnit")
                .isSystemField(true)
                .id(6L)
                .valueBinding("spatialUnit")
                .concept(spatialConcept)
                .build();

        dateField.setId(2L);
        typeField.setId(1L);
        authorField.setId(3L);
        contributorsField.setId(4L);
        actionField.setId(5L);
        spatialField.setId(6L);
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
                        .action(TableColumnAction.GO_TO_RECORDING_UNIT)

                        // CommandLink behavior
                        .processExpr(THIS)
                        .updateExpr("flow")
                        .onstartJs(PF_BUI_CONTENT_SHOW)
                        .oncompleteJs(PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("identifier")
                        .headerKey("recordingunit.field.identifier")
                        .field(recordingUnitIdField)
                        .sortable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("type")
                        .headerKey("recordingunit.property.type")
                        .field(typeField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(true)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("matrixColor")
                        .headerKey("recordingunit.field.matrixColor")
                        .field(matrixColor)
                        .sortable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("openingDate")
                        .headerKey("recordingunit.field.openingDate")
                        .field(dateField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("author")
                        .headerKey("recordingunit.field.author")
                        .field(authorField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("contributors")
                        .headerKey("recordingunit.field.contributors")
                        .field(contributorsField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("action")
                        .headerKey("recordingunit.field.actionUnit")
                        .field(actionField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .readOnly(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("spatial")
                        .headerKey("recordingunit.field.spatialUnit")
                        .field(spatialField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .readOnly(false)
                        .required(false)
                        .build()
        );



        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("specimen")
                        .headerKey("common.entity.specimen")
                        .headerIcon("bi bi-bucket")
                        .visible(true)
                        .toggleable(true)

                        .countKey("specimenList")

                        .viewIcon(BI_BI_EYE)
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(2)

                        .addEnabled(false)
                        .addIcon(BI_BI_PLUS_SQUARE)
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("specimenCreateAllowed")

                        .processExpr(THIS)
                        .updateExpr("flow")
                        .onstartJs(PF_BUI_CONTENT_SHOW)
                        .oncompleteJs(PF_BUI_CONTENT_HIDE_HANDLE_SCROLL_TO_TOP)
                        .build()
        );



    }
}
