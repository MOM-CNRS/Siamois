package fr.siamois.domain.models.form.rules;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;

/**
 * What a rule can do with a field's value, which decides the operators and the kind of value it
 * accepts: a concept is matched against concepts, a number or a date can be ordered, a text is
 * compared as is. Every other field (references, relations…) can only be tested for emptiness.
 */
public enum RuleFieldFamily {
    CONCEPT, NUMBER, DATE, TEXT, OTHER;

    public static RuleFieldFamily of(CustomField field) {
        if (field instanceof CustomFieldConcept) return CONCEPT;
        if (field instanceof CustomFieldInteger || field instanceof CustomFieldDecimal
                || field instanceof CustomFieldMeasurement) return NUMBER;
        if (field instanceof CustomFieldDateTime) return DATE;
        if (field instanceof CustomFieldText) return TEXT;
        return OTHER;
    }

    public boolean isOrdered() {
        return this == NUMBER || this == DATE;
    }
}
