package fr.siamois.ui.form;

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

    public void prepareNewField() {
        this.showEditor = true;
    }

    public void cancelNewField() {
        this.showEditor = false;
    }

    public void saveNewField() {
        // 1. Create the CustomField entity
        // 2. If it's a "Lien" type, create the Relationship Template (relTemplateId)
        // 3. Add to the 'rows' list of the current panel
        // 4. Reset
        this.showEditor = false;
    }

}
