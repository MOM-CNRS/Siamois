package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.rules.FieldRules;

import java.util.Collection;

/**
 * Abstraction over "where do my fields come from?".
 *
 * Implementations:
 *  - Single-entity panels: traverse CustomForm (panels -> rows -> cols)
 *  - List rows: wrap your table column descriptors
 */
public interface FieldSource {

    /**
     * @return all fields participating in this form (no particular order required).
     */
    Collection<CustomField> getAllFields();

    /**
     * Find a field by its ID (used for the fieldIds rules reference).
     */
    CustomField findFieldById(Long id);

    /**
     * @return the conditional rules the field's column declares, {@link FieldRules#NONE} if none.
     */
    FieldRules getRules(CustomField field);
}
