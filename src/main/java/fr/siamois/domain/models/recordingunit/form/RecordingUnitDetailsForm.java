package fr.siamois.domain.models.recordingunit.form;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.models.form.customform.DependsOnJson;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import fr.siamois.ui.form.dto.ColumnWidth;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

import java.util.List;

/**
 * The RecordingUnit details/edit tab form. Ports the "stratigraphic" layout that was previously
 * seeded into the database ({@code DefaultFormsDatasetInitializer}) — the superset of fields,
 * including the erosion/interpretation conditional rules. Type-based field reduction (hiding
 * fields not relevant to a given type) is deferred to the (currently mocked) field-configuration
 * mechanism rather than baked into multiple hardcoded layouts.
 * <p>
 * {@code actionUnit} is placed here too, but hidden ({@code d-none}) and read-only: it needs to be
 * a real, configurable system field (so the field-configuration screen and table columns can see
 * it), yet is not meant to be edited from this form.
 */
public class RecordingUnitDetailsForm extends RecordingUnitForm {

    /** Panel users add their own measurement fields to; those fields are re-injected here on reopen. */
    public static final String MEASUREMENTS_PANEL_NAME = "recordingunit.panel.measurements";

    private static final String SELECT_ONE_FROM_FIELD_CODE_ANSWER_CLASS =
            "fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldAnswerCode";
    private static final String EROSION_ANSWER_VOCABULARY_EXT_ID = "th230";
    private static final String EROSION_ANSWER_CONCEPT_EXT_ID = "4287639";

    private RecordingUnitDetailsForm() {
        throw new UnsupportedOperationException();
    }

    public static FormUiDto build() {
        EnabledWhenJson erosionEnabledWhen = erosionEnabledWhen();
        DependsOnJson interpretationDependsOnNature = dependsOnNature();

        return new FormUiDto.Builder()
                .addPanel(generalPanel(erosionEnabledWhen, interpretationDependsOnNature))
                .addPanel(chronologyPanel())
                .addPanel(measurementsPanel())
                .addPanel(datesPanel())
                .build();
    }

    private static CustomFormPanelUiDto generalPanel(EnabledWhenJson erosionEnabledWhen, DependsOnJson interpretationDependsOnNature) {
        return new CustomFormPanelUiDto.Builder()
                .name(COMMON_HEADER_GENERAL)
                .isSystemPanel(true)
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(SPATIAL_UNIT_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(PARENTS_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(CHILDREN_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).isRequired(true).field(RECORDING_UNIT_TYPE_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(NATURE_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(GEOMORPHO_AGENT_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(INTERPRETATION_FIELD)
                                        .dependsOnSpec(interpretationDependsOnNature).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(MATRIX_COLOR_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).hidden(true).readOnly(true).field(ACTION_UNIT_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).hidden(true).readOnly(true).field(FULL_IDENTIFIER_FIELD).build())
                                .build()
                )
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(EROSION_SHAPE_FIELD)
                                        .enabledWhenSpec(erosionEnabledWhen).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(EROSION_PROFILE_FIELD)
                                        .enabledWhenSpec(erosionEnabledWhen).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(EROSION_ORIENTATION_FIELD)
                                        .enabledWhenSpec(erosionEnabledWhen).build())
                                .build()
                )
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.FULL).field(DESCRIPTION_FIELD).build())
                                .build()
                )
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.FULL).field(COMMENTS_FIELD).build())
                                .build()
                )
                .build();
    }

    private static CustomFormPanelUiDto chronologyPanel() {
        return new CustomFormPanelUiDto.Builder()
                .name("recordingunit.panel.chronology")
                .isSystemPanel(true)
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(CHRONOLOGICAL_PHASE_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(TPQ_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(TAQ_FIELD).build())
                                .build()
                )
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.FULL).field(PHASES_FIELD).build())
                                .build()
                )
                .build();
    }

    private static CustomFormPanelUiDto measurementsPanel() {
        return new CustomFormPanelUiDto.Builder()
                .name(MEASUREMENTS_PANEL_NAME)
                .isSystemPanel(true)
                .canUserAddField(true)
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.HALF).field(Z_INF_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.HALF).field(Z_SUP_FIELD).build())
                                .build()
                )
                .build();
    }

    private static CustomFormPanelUiDto datesPanel() {
        return new CustomFormPanelUiDto.Builder()
                .name(COMMON_HEADER_GENERAL)
                .isSystemPanel(true)
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).isRequired(true).field(OPENING_DATE_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(CLOSING_DATE_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).isRequired(true).field(AUTHOR_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(CONTRIBUTORS_FIELD).build())
                                .build()
                )
                .build();
    }

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

    private static DependsOnJson dependsOnNature() {
        DependsOnJson dependsOn = new DependsOnJson();
        dependsOn.setFieldId(NATURE_FIELD.getId());
        return dependsOn;
    }
}
