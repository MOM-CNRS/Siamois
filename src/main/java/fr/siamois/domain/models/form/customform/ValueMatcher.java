package fr.siamois.domain.models.form.customform;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;

// Compare la valeur (pas l'instance) de l'answer courante au JSON attendu
public interface ValueMatcher {
    boolean matches(CustomFieldAnswerViewModel current);
    Class<?> expectedAnswerClass(); // pour debug
}