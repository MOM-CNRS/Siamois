package fr.siamois.domain.models.specimen.form;

import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;

public class SpecimenNewUnitForm extends SpecimenForm {

    public static FormUiDto build() {
        return new FormUiDto.Builder()
                .addPanel(
                        new CustomFormPanelUiDto.Builder()
                                .name("common.header.general")
                                .isSystemPanel(true)
                                .addRow(
                                        new CustomRowUiDto.Builder()
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .isRequired(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(authorsField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .isRequired(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(collectorsField)
                                                        .build())
                                                .build()
                                )
                                .addRow(
                                        new CustomRowUiDto.Builder()
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .isRequired(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(specimenCategoryField)
                                                        .build())
                                                .addColumn(new CustomColUiDto.Builder()
                                                        .readOnly(false)
                                                        .isRequired(true)
                                                        .className(COLUMN_CLASS_NAME)
                                                        .field(collectionDateField)
                                                        .build())
                                                .build()
                                ).build()
                )
                .build();
    }

}
