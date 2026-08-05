package fr.siamois.domain.models.recordingunit.form;

import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

/**
 * The "new recording unit" dialog form. Reproduces exactly what was previously inlined as
 * {@code RecordingUnit.NEW_UNIT_FORM}.
 */
public class RecordingUnitNewForm extends RecordingUnitForm {

    private RecordingUnitNewForm() {
        throw new UnsupportedOperationException();
    }

    public static FormUiDto build() {
        return new FormUiDto.Builder()
                .addPanel(
                        new CustomFormPanelUiDto.Builder()
                                .name(COMMON_HEADER_GENERAL)
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRowUiDto.Builder()
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .isRequired(true)
                                                        .field(ACTION_UNIT_FIELD)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .isRequired(false)
                                                        .field(SPATIAL_UNIT_FIELD)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .isRequired(true)
                                                        .field(AUTHOR_FIELD)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .isRequired(true)
                                                        .field(RECORDING_UNIT_TYPE_FIELD)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
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
