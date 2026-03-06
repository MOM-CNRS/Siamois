package fr.siamois.ui.viewmodel;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class CustomFormResponseViewModel {

    private CustomForm form;
    private Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();

}
