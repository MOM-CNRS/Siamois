package fr.siamois.ui.table.definitions;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.table.*;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class RecordingUnitTableDefinitionFactory {

    private RecordingUnitTableDefinitionFactory() {}

    /**
     * Applies the standard RecordingUnit columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<RecordingUnit, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        Concept TYPE_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4287605")
                .build();
        Concept OPENINGDATE_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286198")
                .build();
        Concept AUTHOR_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286195")
                .build();
        Concept CONTRIBUTORS_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286195")
                .build();
        Concept ACTION_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286244")
                .build();
        Concept SPATIAL_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286245")
                .build();
        CustomFieldSelectOneFromFieldCode TYPE_FIELD =  CustomFieldSelectOneFromFieldCode.builder()
                .label("recordingunit.property.type")
                .isSystemField(true)
                .valueBinding("type")
                .concept(TYPE_CONCEPT)
                .fieldCode("SIARU.TYPE")
                .styleClass("mr-2 recording-unit-type-chip")
                .build();
        CustomFieldDateTime DATE_FIELD = new CustomFieldDateTime.Builder()
                .label("recordingunit.field.openingDate")
                .isSystemField(true)
                .valueBinding("openingDate")
                .concept(OPENINGDATE_CONCEPT)
                .showTime(false)
                .build();
        CustomFieldSelectOnePerson AUTHOR_FIELD = new CustomFieldSelectOnePerson.Builder()
                .label("recordingunit.field.author")
                .isSystemField(true)
                .valueBinding("author")
                .concept(AUTHOR_CONCEPT)
                .build();
        CustomFieldSelectMultiplePerson CONTRIBUTORS_FIELD = new CustomFieldSelectMultiplePerson.Builder()
                .label("recordingunit.field.contributors")
                .isSystemField(true)
                .valueBinding("contributors")
                .concept(CONTRIBUTORS_CONCEPT)
                .build();
        CustomFieldSelectOneActionUnit ACTION_FIELD = new CustomFieldSelectOneActionUnit.Builder()
                .label("recordingunit.field.actionUnit")
                .isSystemField(true)
                .valueBinding("actionUnit")
                .concept(ACTION_CONCEPT)
                .build();
        CustomFieldSelectOneSpatialUnit SPATIAL_FIELD = new CustomFieldSelectOneSpatialUnit.Builder()
                .label("recordingunit.field.spatialUnit")
                .isSystemField(true)
                .valueBinding("spatialUnit")
                .concept(SPATIAL_CONCEPT)
                .build();

        DATE_FIELD.setId(2L);
        TYPE_FIELD.setId(1L);
        AUTHOR_FIELD.setId(3L);
        CONTRIBUTORS_FIELD.setId(4L);
        ACTION_FIELD.setId(5L);
        SPATIAL_FIELD.setId(6L);
        tableModel.getTableDefinition().addColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.recordingunit.column.identifier")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(true)
                        .sortField("full_identifier")

                        // What to display inside <h:outputText>
                        .valueKey("fullIdentifier")

                        // What to do on click (Pattern A key)
                        .action(TableColumnAction.GO_TO_RECORDING_UNIT)

                        // CommandLink behavior
                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("type")
                        .headerKey("recordingunit.property.type")
                        .field(TYPE_FIELD)
                        .sortable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("openingDate")
                        .headerKey("recordingunit.field.openingDate")
                        .field(DATE_FIELD)
                        .sortable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("author")
                        .headerKey("recordingunit.field.author")
                        .field(AUTHOR_FIELD)
                        .sortable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("contributors")
                        .headerKey("recordingunit.field.contributors")
                        .field(CONTRIBUTORS_FIELD)
                        .sortable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("action")
                        .headerKey("recordingunit.field.actionUnit")
                        .field(ACTION_FIELD)
                        .sortable(true)
                        .visible(true)
                        .readOnly(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("spatial")
                        .headerKey("recordingunit.field.spatialUnit")
                        .field(SPATIAL_FIELD)
                        .sortable(true)
                        .visible(true)
                        .readOnly(false)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("parents")
                        .headerKey("table.spatialunit.column.parents")
                        .headerIcon("bi bi-pencil-square")
                        .visible(true)
                        .toggleable(true)

                        .countKey("parents")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("recordingUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("childre,")
                        .headerKey("table.spatialunit.column.children")
                        .headerIcon("bi bi-pencil-square")
                        .visible(true)
                        .toggleable(true)

                        .countKey("children")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("recordingUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
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

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("specimenCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );



    }
}
