package fr.siamois.infrastructure.database.initializer.seeder.customfield;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomFieldSeeder {

    private final CustomFieldRepository customFieldRepository;
    private final ConceptSeeder conceptSeeder;


    public CustomField findFieldOrReturnNull(CustomFieldSeederSpec s, Concept c) {
        return customFieldRepository.findByTypeAndSystemAndBindingAndConcept(
                s.answerClass(),
                s.isSystemField(),
                s.valueBinding(),
                c
        ).orElse(null);
    }

    public CustomField findFieldOrThrow(CustomFieldSeederSpec s) {
        Concept c = conceptSeeder.findConceptOrThrow(s.conceptKey());
        return customFieldRepository.findByTypeAndSystemAndBindingAndConcept(
                s.answerClass(),
                s.isSystemField(),
                s.valueBinding(),
                c
        ).orElseThrow(() -> new IllegalStateException("Can't find field in Db"));
    }

    public void seed(List<CustomFieldSeederSpec> specs) throws DatabaseDataInitException {
        for (var s : specs) {

            // Check if concept exists
            Concept c = conceptSeeder.findConceptOrThrow(s.conceptKey());

            CustomField field = findFieldOrReturnNull(s,c);
            if(field == null) {
                CustomField f = null;
                try {
                    f = s.answerClass()
                            .getDeclaredConstructor()
                            .newInstance();
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException e) {
                    throw new DatabaseDataInitException(e.getMessage(), e.getCause());
                }

                f.setIsSystemField(s.isSystemField());
                f.setValueBinding(s.valueBinding());
                f.setConcept(c);
                f.setLabel(s.label());


                if (f instanceof CustomFieldSelectOneFromFieldCode df) {
                    df.setStyleClass(s.styleClass());
                    df.setIconClass(s.iconClass());
                    df.setFieldCode(s.fieldCode());
                }

                else if (f instanceof CustomFieldText df) {
                    df.setIsTextArea(s.isTextArea());
                }

                else if(f instanceof CustomFieldDateTime df) {
                    df.setShowTime(false);
                }

                customFieldRepository.save(f);
            }
        }
    }

}
