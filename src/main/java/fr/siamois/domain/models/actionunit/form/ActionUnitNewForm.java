package fr.siamois.domain.models.actionunit.form;

import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

public class ActionUnitNewForm extends ActionUnitForm {

    private ActionUnitNewForm() {
        throw new UnsupportedOperationException();
    }

    public static FormUiDto build() {
        return new FormUiDto.Builder()
                .addPanel(
                        new CustomFormPanelUiDto.Builder()
                                .name(SPATIAL_UNIT_CONTEXT_LABEL_CODE)
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRowUiDto.Builder()
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(MAIN_LOCATION_FIELD)
                                                        .build())
                                                .build()
                                )
                                .addRow(
                                        new CustomRowUiDto.Builder()
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className("ui-g-12")
                                                        .field(SPATIAL_CONTEXT_FIELD)
                                                        .build())
                                                .build()
                                ).build()
                )
                .addPanel(
                        new CustomFormPanelUiDto.Builder()
                                .name(GENERAL_LABEL_CODE)
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRowUiDto.Builder()
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .isRequired(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(NAME_FIELD)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(IDENTIFIER_FIELD)
                                                        .isRequired(true)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .isRequired(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(ACTION_UNIT_TYPE_FIELD)
                                                        .build())
                                                .build()
                                ).build()
                )
                .build();
    }

}
