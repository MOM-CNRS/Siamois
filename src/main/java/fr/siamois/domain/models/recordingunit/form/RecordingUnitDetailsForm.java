package fr.siamois.domain.models.recordingunit.form;

import fr.siamois.domain.models.form.rules.Condition;
import fr.siamois.domain.models.form.rules.FieldConstraint;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldValueSpec;
import fr.siamois.domain.models.form.rules.OptionsFilter;
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

    private static final String EROSION_ANSWER_VOCABULARY_EXT_ID = "th252";
    private static final String EROSION_ANSWER_CONCEPT_EXT_ID = "4287639";

    private RecordingUnitDetailsForm() {
        throw new UnsupportedOperationException();
    }

    public static FormUiDto build() {
        return new FormUiDto.Builder()
                .addPanel(generalPanel())
                .addPanel(chronologyPanel())
                .addPanel(measurementsPanel())
                .addPanel(datesPanel())
                .build();
    }

    private static CustomFormPanelUiDto generalPanel() {
        FieldRules erosionRules = erosionRules();
        return new CustomFormPanelUiDto.Builder()
                .name(COMMON_HEADER_GENERAL)
                .isSystemPanel(true)
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(SPATIAL_UNIT_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(PARENTS_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(CHILDREN_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).readOnly(true).field(STRATIGRAPHIC_RELATIONSHIPS_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).readOnly(true).field(FINDS_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).isRequired(true).field(RECORDING_UNIT_TYPE_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(NATURE_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(GEOMORPHO_AGENT_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(INTERPRETATION_FIELD)
                                        .rules(interpretationRules()).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(MATRIX_COLOR_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).hidden(true).readOnly(true).field(ACTION_UNIT_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).hidden(true).readOnly(true).field(FULL_IDENTIFIER_FIELD).build())
                                .build()
                )
                .addRow(
                        new CustomRowUiDto.Builder()
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(EROSION_SHAPE_FIELD)
                                        .rules(erosionRules).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(EROSION_PROFILE_FIELD)
                                        .rules(erosionRules).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(EROSION_ORIENTATION_FIELD)
                                        .rules(erosionRules).build())
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
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(TAQ_FIELD)
                                        .rules(FieldRules.NONE.withConstraints(FieldConstraint.gte(TPQ_FIELD.getId()))).build())
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
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.HALF).field(Z_SUP_FIELD)
                                        .rules(FieldRules.NONE.withConstraints(FieldConstraint.gte(Z_INF_FIELD.getId()))).build())
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
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(CLOSING_DATE_FIELD)
                                        .rules(FieldRules.NONE.withConstraints(FieldConstraint.gte(OPENING_DATE_FIELD.getId()))).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).isRequired(true).field(AUTHOR_FIELD).build())
                                .addColumn(new CustomColUiDto.Builder().width(ColumnWidth.STANDARD).field(CONTRIBUTORS_FIELD).build())
                                .build()
                )
                .build();
    }

    /** Forme, profil et orientation d'érosion : actifs seulement si la nature est « érosion ». */
    private static FieldRules erosionRules() {
        return FieldRules.NONE.withEnabledWhen(Condition.eq(NATURE_FIELD.getId(),
                FieldValueSpec.concept(EROSION_ANSWER_VOCABULARY_EXT_ID, EROSION_ANSWER_CONCEPT_EXT_ID)));
    }

    /** Interprétation : concepts liés à la nature choisie. */
    private static FieldRules interpretationRules() {
        return FieldRules.NONE.withOptions(new OptionsFilter.RelatedConcepts(NATURE_FIELD.getId()));
    }
}
