package fr.siamois.ui.table;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.ui.form.FieldColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Column backed by a CustomField.
 * Reuses form rendering logic in tables.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class FormFieldColumn extends TableColumn implements FieldColumn {

    /** Associated field */
    private CustomField field;

    /** Required in table context */
    private boolean required;

    /** Read-only in table context */
    private boolean readOnly;

    @Override
    public CustomField getField() {
        return field;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public TableColumnType getType() {
        return TableColumnType.FORM_FIELD;
    }
}
