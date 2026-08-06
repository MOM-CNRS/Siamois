package fr.siamois.domain.models.phase.form;

import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

public class PhaseNewUnitForm extends PhaseForm {

    public static FormUiDto build() {
        return new FormUiDto.Builder()
                .addPanel(
                        new CustomFormPanelUiDto.Builder()
                                .name("common.header.general")
                                .isSystemPanel(true)
                                .addRow(new CustomRowUiDto.Builder()
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .isRequired(true)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(identifierField)
                                                .build())
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .isRequired(true)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(typeField)
                                                .build())
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(titleField)
                                                .build())
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .isRequired(true)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(actionUnitField)
                                                .build())
                                        .build())
                                .build()
                )
                .build();
    }
}
