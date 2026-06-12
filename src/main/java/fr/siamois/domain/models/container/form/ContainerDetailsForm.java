package fr.siamois.domain.models.container.form;

import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

public class ContainerDetailsForm extends ContainerForm {

    public static CustomForm build() {
        return new CustomForm.Builder()
                .name("Details tab form")
                .description("Contains the main form")
                .addPanel(
                        new CustomFormPanel.Builder()
                                .name("common.header.general")
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRow.Builder()
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(identifierField)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(typeField)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(spatialUnitField)
                                                        .build())
                                                .build()
                                )
                                .build()
                )
                .addPanel(
                        new CustomFormPanel.Builder()
                                .name("common.header.dimensions")
                                .isSystemPanel(true)
                                .canUserAddField(true)
                                .addRow(
                                        new CustomRow.Builder()
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(lengthField)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(widthField)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(heightField)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
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
