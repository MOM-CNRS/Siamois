package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.form.customfield.CustomField;

/**
 * A system field of a configurable table, with the properties that belong to the field itself rather
 * than to a layout: whether it is {@code hidden} from the form body (identifiers and the owning
 * project, shown elsewhere) and whether it is {@code readOnly} (relations computed from other
 * records, never written through the form).
 *
 * @param field    the field definition, shared: never mutate it
 * @param hidden   true if the form never renders it in its body
 * @param readOnly true if it can never be edited
 */
public record SystemFieldSpec(CustomField field, boolean hidden, boolean readOnly) {

    public static SystemFieldSpec of(CustomField field) {
        return new SystemFieldSpec(field, false, false);
    }

    public static SystemFieldSpec readOnly(CustomField field) {
        return new SystemFieldSpec(field, false, true);
    }

    /** Carried by every form for the client's header (the type), never laid out in the body, still editable. */
    public static SystemFieldSpec hidden(CustomField field) {
        return new SystemFieldSpec(field, true, false);
    }

    public static SystemFieldSpec hiddenReadOnly(CustomField field) {
        return new SystemFieldSpec(field, true, true);
    }
}
