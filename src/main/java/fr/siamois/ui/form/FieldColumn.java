package fr.siamois.ui.form;

import fr.siamois.domain.models.form.customfield.CustomField;

public interface FieldColumn {

    CustomField getField();

    /**
     * Si la colonne peut être requise pour la saisie
     * (dans un CustomForm c'est défini par la base ;
     * dans une TableColumn, tu définis la règle UI).
     */
    default boolean isRequired() {
        return false;
    }

    /**
     * Si la colonne doit être affichée en mode lecture seule
     */
    default boolean isReadOnly() {
        return false;
    }
}
