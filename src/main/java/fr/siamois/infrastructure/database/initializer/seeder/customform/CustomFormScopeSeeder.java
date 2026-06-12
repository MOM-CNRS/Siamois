package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.formscope.FormScope;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.SeederUtils;
import fr.siamois.infrastructure.database.repositories.form.FormScopeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomFormScopeSeeder {

    private final ConceptSeeder conceptSeeder;
    private final CustomFormSeeder customFormSeeder;
    private final FormScopeRepository formScopeRepository;

    public FormScope findOrReturnNull(Concept c, CustomForm f) {
        return formScopeRepository.findGlobalByTypeAndForm(c,f)
        .orElse(null);
    }

    public void seed(List<CustomFormScopeDTO> specs) {
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                Concept c = null;
                if(s.type() != null) {
                    c = SeederUtils.field("type", () -> conceptSeeder.findConceptOrThrow(s.type()));
                }

                CustomForm f = SeederUtils.field("form", () -> customFormSeeder.findOrThrow(s.form()));
                FormScope scope = findOrReturnNull(c,f);

                if(scope == null) {
                    scope = new FormScope();
                    scope.setScopeLevel(FormScope.ScopeLevel.GLOBAL_DEFAULT);
                    scope.setType(c);
                    scope.setForm(f);
                    formScopeRepository.save(scope);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Scope formulaire ligne " + (i + 1) + "] '" + s.form().name() + "' : " + e.getMessage(), e);
            }
        }
    }

}
