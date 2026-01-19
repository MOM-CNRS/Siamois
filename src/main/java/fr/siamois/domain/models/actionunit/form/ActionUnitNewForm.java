package fr.siamois.domain.models.actionunit.form;

import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

public class ActionUnitNewForm extends ActionUnitForm {

    private ActionUnitNewForm() {
        throw new UnsupportedOperationException();
    }

    public static CustomForm build() {
        return new CustomForm.Builder()
                .name(DETAIL_TAB_NAME)
                .description("")
                .addPanel(
                        new CustomFormPanel.Builder()
                                .name(SPATIAL_UNIT_CONTEXT_LABEL_CODE)
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRow.Builder()
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className("ui-g-12")
                                                        .field(SPATIAL_CONTEXT_FIELD)
                                                        .build())
                                                .build()
                                ).build()
                )
                .addPanel(
                        new CustomFormPanel.Builder()
                                .name(GENERAL_LABEL_CODE)
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRow.Builder()
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .isRequired(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(NAME_FIELD)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
                                                        .readOnly(false)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(IDENTIFIER_FIELD)
                                                        .isRequired(true)
                                                        .build())
                                                .addColumn(new CustomCol.Builder()
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
