package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomFieldAnswerRepository extends CrudRepository<CustomFieldAnswer, CustomFieldAnswer.CustomFieldAnswerId> {
    Optional<CustomFieldAnswer> findByFormConfigAnswerAndCustomField(FormConfigAnswer formConfigAnswer, CustomField customField);
}
