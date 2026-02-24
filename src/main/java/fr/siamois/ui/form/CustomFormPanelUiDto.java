package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customform.CustomRow;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CustomFormPanelUiDto implements Serializable {

    private String className;
    private String name;
    private List<CustomRowUiDto> rows;
    private Boolean isSystemPanel; // define by system or user

}
