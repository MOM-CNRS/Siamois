package fr.siamois.domain.models.form.customfieldanswer.basetypes;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;

import java.util.Objects;

/**
 * Answer to a yes/no field. {@code null} is "not answered", which only a field that allows it
 * ({@code CustomFieldBoolean#isNoAnswerAllowed()}) offers to the user.
 */
@Data
@Entity
@DiscriminatorValue("BOOLEAN")
public class CustomFieldAnswerBoolean extends CustomFieldAnswer {

    @Column(name = "value_as_boolean")
    private Boolean value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerBoolean that)) return false;
        if (!super.equals(o)) return false; // Ensures inherited fields are compared

        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public void setValue(Object value) {
        if (value == null || value instanceof Boolean) {
            this.value = (Boolean) value;
        } else {
            throw new IllegalArgumentException("Value must be a Boolean");
        }
    }
}
