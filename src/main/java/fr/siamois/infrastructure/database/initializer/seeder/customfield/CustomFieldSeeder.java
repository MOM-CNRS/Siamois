package fr.siamois.infrastructure.database.initializer.seeder.customfield;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import io.micrometer.common.lang.Nullable;
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
        return customFieldRepository.findByTypeAndSystemAndBindingAndIconClassAndStyleClassAndConcept(
                s.answerClass(),
                s.isSystemField(),
                s.valueBinding(),
                s.styleClass(),
                s.iconClass(),
                c
        ).orElse(null);
    }

    public CustomField findFieldOrThrow(CustomFieldSeederSpec s) {
        Concept c = conceptSeeder.findConceptOrThrow(s.conceptKey());
        return customFieldRepository.findByTypeAndSystemAndBindingAndIconClassAndStyleClassAndConcept(
                s.answerClass(),
                s.isSystemField(),
                s.valueBinding(),
                s.styleClass(),
                s.iconClass(),
                c
        ).orElseThrow(() -> new IllegalStateException("Can't find concept in Db"));
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
                f.setStyleClass(s.styleClass());
                f.setIconClass(s.iconClass());
                f.setFieldCode(s.fieldCode());
                f.setConcept(c);

                customFieldRepository.save(f);
            }
        }
    }

}
