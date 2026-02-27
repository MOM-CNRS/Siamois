package fr.siamois.ui.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;

/**
 * Fournit la réponse courante d'un champ pour l'instance de formulaire affichée.
 * Peut retourner null si aucune réponse n'est présente.
 */
@FunctionalInterface
public interface ValueProvider {
    CustomFieldAnswerViewModel getCurrentAnswer(CustomField field);
}

