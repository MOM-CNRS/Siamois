package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import fr.siamois.ui.form.field.CustomFieldUiDto;

public class CustomColUiDto {

    private boolean readOnly = false;
    private boolean isRequired = false;
    private CustomField field;
    private String className;
    private EnabledWhenJson enabledWhenSpec;

}
