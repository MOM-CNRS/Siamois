package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customform.CustomFormPanel;
import lombok.Data;


import java.util.List;

@Data
public class FormUiDto {

    private List<CustomFormPanelUiDto> layout;

}
