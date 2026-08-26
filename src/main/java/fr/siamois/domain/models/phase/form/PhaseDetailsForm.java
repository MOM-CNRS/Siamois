package fr.siamois.domain.models.phase.form;

import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

public class PhaseDetailsForm extends PhaseForm {

    public static FormUiDto build() {
        return new FormUiDto.Builder()
                .addPanel(
                        new CustomFormPanelUiDto.Builder()
                                .name("common.header.general")
                                .isSystemPanel(true)
                                .addRow(new CustomRowUiDto.Builder()
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(true)
                                                .className("d-none")
                                                .field(identifierField)
                                                .build())
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(typeField)
                                                .build())
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(titleField)
                                                .build())
                                        .build())
                                .addRow(new CustomRowUiDto.Builder()
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(orderNumberField)
                                                .build())
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(keywordsField)
                                                .build())
                                        .build())
                                .addRow(new CustomRowUiDto.Builder()
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .className("ui-g-12 ui-md-12 ui-lg-12")
                                                .field(descriptionField)
                                                .build())
                                        .build())
                                .build()
                )
                .addPanel(
                        new CustomFormPanelUiDto.Builder()
                                .name("common.header.chronologie")
                                .isSystemPanel(true)
                                .addRow(new CustomRowUiDto.Builder()
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(periodsField)
                                                .build())
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(lowerBoundField)
                                                .build())
                                        .addColumn(new CustomColUiDto.Builder()
                                                .readOnly(false)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(upperBoundField)
                                                .build())
                                        .build())
                                .build()
                )
                .build();
    }
}
