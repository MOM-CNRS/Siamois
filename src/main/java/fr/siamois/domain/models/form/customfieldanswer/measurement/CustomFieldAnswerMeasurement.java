package fr.siamois.domain.models.form.customfieldanswer.measurement;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Answer to a measurement field added to a form, stored inline like the text and integer answers.
 * <p>
 * The number, its comment and the unit it was entered in are all stored here. The unit is normally
 * the one carried by the field definition ({@code CustomFieldMeasurement#getUnit()}), but it is
 * kept on the answer so that a stored number stays readable as what it was measured in, even after
 * the field itself is switched to another unit.
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_unit_id")
    private UnitDefinition unit;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerMeasurement that)) return false;
        if (!super.equals(o)) return false; // Ensures inherited fields are compared

        return Objects.equals(value, that.value)
                && Objects.equals(comment, that.comment)
                && Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, comment, unit);
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
