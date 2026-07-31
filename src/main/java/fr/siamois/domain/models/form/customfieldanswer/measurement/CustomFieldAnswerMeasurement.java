package fr.siamois.domain.models.form.customfieldanswer.measurement;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Answer to a measurement field added to a form, stored inline like the text and integer answers.
 * <p>
 * Only what the user types is stored here — the number and its comment. The unit is a property of
 * the field itself ({@code CustomFieldMeasurement#getUnit()}), the same for every answer to that
 * field, so it is read back from the field definition rather than duplicated on each answer.
 */
@Entity
@Data
@DiscriminatorValue("MEASUREMENT")
@NoArgsConstructor
public class CustomFieldAnswerMeasurement extends CustomFieldAnswer {

    @Column(name = "value_as_double")
    private Double value;

    @Column(name = "value_comment")
    private String comment;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerMeasurement that)) return false;
        if (!super.equals(o)) return false; // Ensures inherited fields are compared

        return Objects.equals(value, that.value) && Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, comment);
    }

    @Override
    public void setValue(Object value) {
        if (value == null || value instanceof Double) {
            this.value = (Double) value;
        } else {
            throw new IllegalArgumentException("Value must be a Double");
        }
    }

}
