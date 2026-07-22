package fr.siamois.domain.models.recordingunit.form;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.form.customform.DependsOnJson;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;

import java.util.List;

/**
 * The RecordingUnit details/edit tab form. Ports the "stratigraphic" layout that was previously
 * seeded into the database ({@code DefaultFormsDatasetInitializer}) — the superset of fields,
 * including the erosion/interpretation conditional rules. Type-based field reduction (hiding
 * fields not relevant to a given type) is deferred to the (currently mocked) field-configuration
 * mechanism rather than baked into multiple hardcoded layouts.
 * <p>
 * The "matrix" fields (composition/color/texture) that exist as seed data but are never actually
 * wired into the live stratigraphic layout are intentionally left out here too, to match today's
 * real rendered output rather than the seeder's unused/dead columns.
 */
public class RecordingUnitDetailsForm extends RecordingUnitForm {

    private static final String UI_G_12_UI_MD_6_UI_LG_3 = "ui-g-12 ui-md-6 ui-lg-3";
    private static final String UI_G_12_UI_MD_12_UI_LG_12 = "ui-g-12 ui-md-12 ui-lg-12";
    private static final String UI_G_12_UI_MD_6_UI_LG_6 = "ui-g-12 ui-md-6 ui-lg-6";

    private static final String SELECT_ONE_FROM_FIELD_CODE_ANSWER_CLASS =
            "fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldCode";
    private static final String EROSION_ANSWER_VOCABULARY_EXT_ID = "th230";
    private static final String EROSION_ANSWER_CONCEPT_EXT_ID = "4287639";

    private RecordingUnitDetailsForm() {
        throw new UnsupportedOperationException();
    }

    public static CustomForm build() {
        EnabledWhenJson erosionEnabledWhen = erosionEnabledWhen();
        DependsOnJson interpretationDependsOnNature = dependsOnNature();

        return new CustomForm.Builder()
                .name("Details tab form")
                .description("Contains the main form")
                .addPanel(generalPanel(erosionEnabledWhen, interpretationDependsOnNature))
                .addPanel(chronologyPanel())
                .addPanel(measurementsPanel())
                .addPanel(datesPanel())
                .build();
    }

    private static CustomFormPanel generalPanel(EnabledWhenJson erosionEnabledWhen, DependsOnJson interpretationDependsOnNature) {
        return new CustomFormPanel.Builder()
                .name(COMMON_HEADER_GENERAL)
                .isSystemPanel(true)
                .addRow(
                        new CustomRow.Builder()
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(SPATIAL_UNIT_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).isRequired(true).field(PARENTS_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).isRequired(true).field(CHILDREN_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).isRequired(true).field(FULL_IDENTIFIER_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).isRequired(true).field(RECORDING_UNIT_TYPE_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(NATURE_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(GEOMORPHO_AGENT_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(INTERPRETATION_FIELD)
                                        .dependsOnSpec(interpretationDependsOnNature).build())
                                .build()
                )
                .addRow(
                        new CustomRow.Builder()
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(EROSION_SHAPE_FIELD)
                                        .enabledWhenSpec(erosionEnabledWhen).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(EROSION_PROFILE_FIELD)
                                        .enabledWhenSpec(erosionEnabledWhen).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(EROSION_ORIENTATION_FIELD)
                                        .enabledWhenSpec(erosionEnabledWhen).build())
                                .build()
                )
                .addRow(
                        new CustomRow.Builder()
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_12_UI_LG_12).field(DESCRIPTION_FIELD).build())
                                .build()
                )
                .addRow(
                        new CustomRow.Builder()
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_12_UI_LG_12).field(COMMENTS_FIELD).build())
                                .build()
                )
                .build();
    }

    private static CustomFormPanel chronologyPanel() {
        return new CustomFormPanel.Builder()
                .name("recordingunit.panel.chronology")
                .isSystemPanel(true)
                .addRow(
                        new CustomRow.Builder()
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(CHRONOLOGICAL_PHASE_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(TPQ_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(TAQ_FIELD).build())
                                .build()
                )
                .addRow(
                        new CustomRow.Builder()
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_12_UI_LG_12).field(PHASES_FIELD).build())
                                .build()
                )
                .build();
    }

    private static CustomFormPanel measurementsPanel() {
        return new CustomFormPanel.Builder()
                .name("recordingunit.panel.measurements")
                .isSystemPanel(true)
                .canUserAddField(true)
                .addRow(
                        new CustomRow.Builder()
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_6).field(Z_INF_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_6).field(Z_SUP_FIELD).build())
                                .build()
                )
                .build();
    }

    private static CustomFormPanel datesPanel() {
        return new CustomFormPanel.Builder()
                .name(COMMON_HEADER_GENERAL)
                .isSystemPanel(true)
                .addRow(
                        new CustomRow.Builder()
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).isRequired(true).field(OPENING_DATE_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(CLOSING_DATE_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).isRequired(true).field(AUTHOR_FIELD).build())
                                .addColumn(new CustomCol.Builder().className(UI_G_12_UI_MD_6_UI_LG_3).field(CONTRIBUTORS_FIELD).build())
                                .build()
                )
                .build();
    }

    /**
     * Erosion shape/profile/orientation are only enabled when Nature (geomorphologicalCycle) == "Erosion".
     */
    private static EnabledWhenJson erosionEnabledWhen() {
        EnabledWhenJson.ValueJson erosionValue = new EnabledWhenJson.ValueJson();
        erosionValue.setAnswerClass(SELECT_ONE_FROM_FIELD_CODE_ANSWER_CLASS);
        ObjectNode erosionConceptNode = JsonNodeFactory.instance.objectNode()
                .put("vocabularyExtId", EROSION_ANSWER_VOCABULARY_EXT_ID)
                .put("conceptExtId", EROSION_ANSWER_CONCEPT_EXT_ID);
        erosionValue.setValue(erosionConceptNode);

        EnabledWhenJson enabledWhen = new EnabledWhenJson();
        enabledWhen.setOp(EnabledWhenJson.Op.EQ);
        enabledWhen.setFieldId(NATURE_FIELD.getId());
        enabledWhen.setValues(List.of(erosionValue));
        return enabledWhen;
    }

    /**
     * Interpretation's autocomplete is restricted to concepts related to whichever Nature was chosen.
     */
    private static DependsOnJson dependsOnNature() {
        DependsOnJson dependsOn = new DependsOnJson();
        dependsOn.setFieldId(NATURE_FIELD.getId());
        return dependsOn;
    }
}
