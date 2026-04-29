package fr.siamois.ui.form.dto;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import lombok.Data;

import java.io.Serializable;

@Data
public class CustomColUiDto implements Serializable {

    private boolean readOnly = false;
    private boolean isRequired = false;
    private boolean canBeRemoved = false;
    private CustomField field;
    private String className;
    private EnabledWhenJson enabledWhenSpec;

}
