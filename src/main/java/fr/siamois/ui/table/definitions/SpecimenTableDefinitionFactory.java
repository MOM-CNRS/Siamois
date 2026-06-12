package fr.siamois.ui.table.definitions;


import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.table.column.CommandLinkColumn;
import fr.siamois.ui.table.column.FormFieldColumn;
import fr.siamois.ui.table.column.TableColumnAction;
import fr.siamois.ui.table.viewmodel.EntityTableViewModel;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;


/**
 * factory that "applies" a reusable column set + toolbar config onto an existing tableModel.
 * Put this in a shared package and call it from panels, tabs, etc.
 */
public final class SpecimenTableDefinitionFactory {

    private static final String THIS = "@this";
    private static final String PF_BUI_CONTENT_SHOW = "PF('buiContent').show()";
    private static final String PF_BUI_CONTENT_HIDE = "PF('buiContent').hide();";

    private SpecimenTableDefinitionFactory() {}

    /**
     * Applies the standard Specimen columns + toolbar create config to the given tableModel.
     *
     * Notes:
     * - Does not call any UI beans (FlowBean, etc.)
     * - Only sets column metadata + generic toolbar create policy.
     * - If you want per-screen overrides, call them AFTER this method.
     */
    public static void applyTo(EntityTableViewModel<SpecimenDTO, ?> tableModel) {
        if (tableModel == null) {
            return;
        }

        // --- Concepts (from SpecimenForm) ---
        Concept idConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286250")
                .build();
        Concept categoryConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4282392")
                .build();
        Concept recordingUnitConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4290139")
                .build();
        Concept isPartOfConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4290140")
                .build();
        Concept containsConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4290141")
                .build();
        Concept otherIdConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4290142")
                .build();
        Concept isolationNumberConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4282391")
                .build();
        Concept authorsConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286246")
                .build();
        Concept collectorsConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286247")
                .build();
        Concept collectionDateConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286249")
                .build();
        Concept materialConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4290157")
                .build();
        Concept materialClassConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4290155")
                .build();
        Concept normalizedInterpretationConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4290156")
                .build();
        Concept chronologicalAttributionConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4290160")
                .build();
        Concept numberOfElementConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4290161")
                .build();

        // --- Fields ---
        CustomFieldText idField = CustomFieldText.builder()
                .label("recordingunit.field.identifier")
                .isSystemField(true)
                .isTextArea(false)
                .id(4L)
                .valueBinding("fullIdentifier")
                .concept(idConcept)
                .build();

        CustomFieldSelectOneFromFieldCode categoryField = CustomFieldSelectOneFromFieldCode.builder()
                .label("specimen.field.category")
                .isSystemField(true)
                .id(9L)
                .valueBinding("category")
                .concept(categoryConcept)
                .fieldCode(Specimen.CAT_FIELD)
                .styleClass("mr-2 specimen-type-chip")
                .iconClass("bi bi-bucket")
                .build();

        CustomFieldSelectOneRecordingUnit recordingUnitField = CustomFieldSelectOneRecordingUnit.builder()
                .label("specimen.field.recordingUnit")
                .isSystemField(true)
                .id(1L)
                .valueBinding("recordingUnit")
                .concept(recordingUnitConcept)
                .build();

        CustomFieldSelectMultipleSpecimen isPartOfField = CustomFieldSelectMultipleSpecimen.builder()
                .label("specimen.field.isPartOf")
                .isSystemField(true)
                .id(2L)
                .valueBinding("parents")
                .concept(isPartOfConcept)
                .build();

        CustomFieldSelectMultipleSpecimen containsField = CustomFieldSelectMultipleSpecimen.builder()
                .label("specimen.field.contains")
                .isSystemField(true)
                .id(3L)
                .valueBinding("children")
                .concept(containsConcept)
                .build();

        CustomFieldText otherIdField = CustomFieldText.builder()
                .label("recordingunit.field.otherIdentifier")
                .isSystemField(true)
                .isTextArea(false)
                .id(5L)
                .valueBinding("otherIdentifier")
                .concept(otherIdConcept)
                .build();

        CustomFieldText isolationNumberField = CustomFieldText.builder()
                .label("recordingunit.field.isolationIdentifier")
                .isSystemField(true)
                .isTextArea(false)
                .id(10L)
                .valueBinding("isolationNumber")
                .concept(isolationNumberConcept)
                .build();

        CustomFieldSelectMultiplePerson authorsField = CustomFieldSelectMultiplePerson.builder()
                .label("specimen.field.authors")
                .isSystemField(true)
                .id(6L)
                .valueBinding("authors")
                .concept(authorsConcept)
                .build();

        CustomFieldSelectMultiplePerson collectorsField = CustomFieldSelectMultiplePerson.builder()
                .label("specimen.field.collectors")
                .isSystemField(true)
                .id(7L)
                .valueBinding("collectors")
                .concept(collectorsConcept)
                .build();

        CustomFieldDateTime collectionDateField = CustomFieldDateTime.builder()
                .label("specimen.field.collectionDate")
                .isSystemField(true)
                .id(11L)
                .valueBinding("collectionDate")
                .showTime(false)
                .concept(collectionDateConcept)
                .build();

        CustomFieldSelectMultipleFromFieldCode materialField = CustomFieldSelectMultipleFromFieldCode.builder()
                .label("specimen.field.material")
                .isSystemField(true)
                .id(13L)
                .fieldCode(Specimen.MATIERE_FIELD)
                .valueBinding("material")
                .concept(materialConcept)
                .build();

        CustomFieldSelectMultipleFromFieldCode materialClassField = CustomFieldSelectMultipleFromFieldCode.builder()
                .label("specimen.field.materialClass")
                .isSystemField(true)
                .id(14L)
                .fieldCode(Specimen.CLASS_FIELD)
                .valueBinding("materialClass")
                .concept(materialClassConcept)
                .build();

        CustomFieldSelectOneFromFieldCode normalizedInterpretationField = CustomFieldSelectOneFromFieldCode.builder()
                .label("specimen.field.normalizedInterpretation")
                .isSystemField(true)
                .id(15L)
                .fieldCode(Specimen.INTERPRETATION_FIELD)
                .valueBinding("normalizedInterpretation")
                .concept(normalizedInterpretationConcept)
                .build();

        CustomFieldSelectOneFromFieldCode chronologicalAttributionField = CustomFieldSelectOneFromFieldCode.builder()
                .label("specimen.field.chronologicalAttribution")
                .isSystemField(true)
                .id(18L)
                .valueBinding("chronologicalAttribution")
                .concept(chronologicalAttributionConcept)
                .build();

        CustomFieldInteger numberOfElementField = CustomFieldInteger.builder()
                .label("specimen.field.numberOfElement")
                .isSystemField(true)
                .id(21L)
                .maxValue(Integer.MAX_VALUE)
                .minValue(0)
                .valueBinding("numberOfElements")
                .concept(numberOfElementConcept)
                .build();

        // --- CommandLink column (non-toggleable identifier chip) ---
        tableModel.getTableDefinition().setCommandLinkColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.recordingunit.column.identifier")
                        .visible(true)
                        .toggleable(false)
                        .sortable(false)
                        .filterable(false)
                        .sortField("full_identifier")
                        .iconClass("bi bi-bucket")
                        .chipColor("var(--ground-main-color)")
                        .valueKey("fullIdentifier")
                        .action(TableColumnAction.GO_TO_SPECIMEN)
                        .processExpr(THIS)
                        .updateExpr("flow")
                        .onstartJs(PF_BUI_CONTENT_SHOW)
                        .oncompleteJs(PF_BUI_CONTENT_HIDE)
                        .build()
        );

        // --- Visible columns ---
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("identifier")
                        .headerKey("recordingunit.field.identifier")
                        .field(idField)
                        .sortable(true)
                        .filterable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("category")
                        .headerKey("specimen.field.category")
                        .field(categoryField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("material")
                        .headerKey("specimen.field.material")
                        .field(materialField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("materialClass")
                        .headerKey("specimen.field.materialClass")
                        .field(materialClassField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("normalizedInterpretation")
                        .headerKey("specimen.field.normalizedInterpretation")
                        .field(normalizedInterpretationField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("recordingUnit")
                        .headerKey("specimen.field.recordingUnit")
                        .field(recordingUnitField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("isPartOf")
                        .headerKey("specimen.field.isPartOf")
                        .field(isPartOfField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("contains")
                        .headerKey("specimen.field.contains")
                        .field(containsField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("authors")
                        .headerKey("specimen.field.authors")
                        .field(authorsField)
                        .sortable(false)
                        .filterable(false)
                        .visible(true)
                        .required(false)
                        .build()
        );

        // --- Hidden/toggleable columns ---
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("chronologicalAttribution")
                        .headerKey("specimen.field.chronologicalAttribution")
                        .field(chronologicalAttributionField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("collectionDate")
                        .headerKey("specimen.field.collectionDate")
                        .field(collectionDateField)
                        .sortable(true)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("collectors")
                        .headerKey("specimen.field.collectors")
                        .field(collectorsField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("numberOfElements")
                        .headerKey("specimen.field.numberOfElement")
                        .field(numberOfElementField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("otherIdentifier")
                        .headerKey("recordingunit.field.otherIdentifier")
                        .field(otherIdField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("isolationNumber")
                        .headerKey("recordingunit.field.isolationIdentifier")
                        .field(isolationNumberField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );

        Concept phasesConcept = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("specimen.phases")
                .build();

        CustomFieldSelectMultiplePhase phasesField = CustomFieldSelectMultiplePhase.builder()
                .label("specimen.field.phases")
                .isSystemField(true)
                .id(24L)
                .valueBinding("phases")
                .concept(phasesConcept)
                .build();

        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("phases")
                        .headerKey("specimen.field.phases")
                        .field(phasesField)
                        .sortable(false)
                        .filterable(false)
                        .visible(false)
                        .required(false)
                        .build()
        );
    }
}