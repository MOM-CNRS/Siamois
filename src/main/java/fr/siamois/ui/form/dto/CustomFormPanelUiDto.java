package fr.siamois.ui.form.dto;

import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CustomFormPanelUiDto implements Serializable {

    private String className;
    private String name;
    private List<CustomRowUiDto> rows;
    private Boolean canUserAddFields;
    private Boolean isSystemPanel; // define by system or user

    private boolean showEditor = false;

}
