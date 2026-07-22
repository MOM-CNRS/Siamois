package fr.siamois.domain.models.recordingunit.form;

import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

/**
 * The "new recording unit" dialog form. Reproduces exactly what was previously inlined as
 * {@code RecordingUnit.NEW_UNIT_FORM}.
 */
public class RecordingUnitNewForm extends RecordingUnitForm {

    private RecordingUnitNewForm() {
        throw new UnsupportedOperationException();
    }

    public static CustomForm build() {
        return new CustomForm.Builder()
                .name("Details tab form")
                .description("Contains the main form")
                .addPanel(
                        new CustomFormPanel.Builder()
                                .name(COMMON_HEADER_GENERAL)
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRow.Builder()
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .isRequired(true)
                                                        .field(ACTION_UNIT_FIELD)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .isRequired(false)
                                                        .field(SPATIAL_UNIT_FIELD)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .isRequired(true)
                                                        .field(AUTHOR_FIELD)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .isRequired(true)
                                                        .field(RECORDING_UNIT_TYPE_FIELD)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .isRequired(true)
                                                        .field(OPENING_DATE_FIELD)
                                                        .build())
                                                .build()
                                ).build()
                )
                .build();
    }
}
