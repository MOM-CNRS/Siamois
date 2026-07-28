package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomFieldAnswerService {

    private final CustomFieldAnswerRepository customFieldAnswerRepository;

    private void createOrUpdateAnswer(FormConfigAnswer formConfigAnswer, CustomField customField, CustomFieldAnswerViewModel customFieldAnswerViewModel) {
        Optional<CustomFieldAnswer> optAnswer = customFieldAnswerRepository.findByFormConfigAnswerAndCustomField(formConfigAnswer, customField);
        CustomFieldAnswer answer;
        if(optAnswer.isPresent()) {
            answer = optAnswer.get();
        } else {
            answer = answerEntityOf(customField);
            answer.setCustomField(customField);
            answer.setFormConfigAnswer(formConfigAnswer);
        }
        answer.setValue(customFieldAnswerViewModel.getValue());
        customFieldAnswerRepository.save(answer);
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(@NonNull CustomFormResponseViewModel response) {
        for (Map.Entry<CustomField, CustomFieldAnswerViewModel> fieldAnswer : response.getAnswers().entrySet()) {
            CustomField field = fieldAnswer.getKey();
            CustomFieldAnswerViewModel answer = fieldAnswer.getValue();
            createOrUpdateAnswer(response.getFormConfig(), field, answer);
        }
    }

    private CustomFieldAnswer answerEntityOf(@NonNull CustomField field) {
        return CustomFieldAnswerFactory.ANSWER_ENTITY_CREATORS.get(field.getClass()).apply(null);
    }

}
