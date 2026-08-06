package fr.siamois.ui.viewmodel;

import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomFormResponseViewModel {

    private FormConfigAnswer formConfig;
    private Map<CustomField, CustomFieldAnswerViewModel> answers;

    public CustomFormResponseViewModel(FormConfigAnswer formConfigAnswer) {
        this(formConfigAnswer, new HashMap<>());
    }
}
