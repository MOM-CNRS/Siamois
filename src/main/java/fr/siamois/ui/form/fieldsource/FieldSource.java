package fr.siamois.ui.form.fieldsource;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;

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
     * Find a field by its ID (used for EnabledWhenJson.fieldId).
     */
    CustomField findFieldById(Long id);

    /**
     * @return the EnabledWhenJson spec for this field, or null if none.
     */
    EnabledWhenJson getEnabledSpec(CustomField field);
}
