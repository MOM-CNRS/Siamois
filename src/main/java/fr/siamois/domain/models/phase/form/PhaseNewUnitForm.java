package fr.siamois.domain.models.phase.form;

import fr.siamois.domain.models.form.customform.*;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

public class PhaseNewUnitForm extends PhaseForm {

    public static CustomForm build() {
        return new CustomForm.Builder()
                .name("New phase form")
                .description("Contains the creation form")
                .addPanel(
                        new CustomFormPanel.Builder()
                                .name("common.header.general")
                                .isSystemPanel(true)
                                .addRow(new CustomRow.Builder()
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
                                        .addColumn(new CustomCol.Builder()
                                                .readOnly(false)
                                                .className(COLUMN_CLASS_NAME)
                                                .field(titleField)
                                                .build())
                                        .build())
                                .build()
                )
                .build();
    }
}
