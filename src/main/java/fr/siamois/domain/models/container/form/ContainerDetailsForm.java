package fr.siamois.domain.models.container.form;

import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

public class ContainerDetailsForm extends ContainerForm {

    public static FormUiDto build() {
        return new FormUiDto.Builder()
                .addPanel(
                        new CustomFormPanelUiDto.Builder()
                                .name("common.header.general")
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRowUiDto.Builder()
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(true)
                                                        .className(COLUMN_CLASS_NAME)
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
                                                        .field(spatialUnitField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(actionUnitField)
                                                        .build())
                                                .build()
                                )
                                .build()
                )
                .addPanel(
                        new CustomFormPanelUiDto.Builder()
                                .name("common.header.dimensions")
                                .isSystemPanel(true)
                                .canUserAddField(true)
                                .addRow(
                                        new CustomRowUiDto.Builder()
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(lengthField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(widthField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(heightField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(weightField)
                                                        .build())
                                                .build()
                                )
                                .build()
                )
                .build();
    }
}
