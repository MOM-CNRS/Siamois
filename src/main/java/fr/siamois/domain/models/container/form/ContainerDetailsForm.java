package fr.siamois.domain.models.container.form;

import fr.siamois.ui.form.dto.ColumnWidth;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;

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
                                                        .width(ColumnWidth.STANDARD).hidden(true)
                                                        .field(identifierField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .width(ColumnWidth.STANDARD)
                                                        .field(typeField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .width(ColumnWidth.STANDARD)
                                                        .field(spatialUnitField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(true)
                                                        .width(ColumnWidth.STANDARD).hidden(true)
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
                                                        .width(ColumnWidth.STANDARD)
                                                        .field(lengthField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .width(ColumnWidth.STANDARD)
                                                        .field(widthField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .width(ColumnWidth.STANDARD)
                                                        .field(heightField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .width(ColumnWidth.STANDARD)
                                                        .field(weightField)
                                                        .build())
                                                .build()
                                )
                                .build()
                )
                .build();
    }
}
