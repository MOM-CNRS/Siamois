package fr.siamois.domain.models.container.form;

import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

public class ContainerNewUnitForm extends ContainerForm {

    public static CustomForm build() {
        return new CustomForm.Builder()
                .name("New container form")
                .description("Contains the creation form")
                .addPanel(
                        new CustomFormPanel.Builder()
                                .name("common.header.general")
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRow.Builder()
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .isRequired(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(identifierField)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .isRequired(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(typeField)
                                                        .build())
                                                .build()
                                )
                                .build()
                )
                .build();
    }
}
