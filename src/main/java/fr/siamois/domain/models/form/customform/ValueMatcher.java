package fr.siamois.domain.models.form.customform;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;

// Compare la valeur (pas l'instance) de l'answer courante au JSON attendu
public interface ValueMatcher {
    boolean matches(CustomFieldAnswer current);
    Class<?> expectedAnswerClass(); // pour debug
}