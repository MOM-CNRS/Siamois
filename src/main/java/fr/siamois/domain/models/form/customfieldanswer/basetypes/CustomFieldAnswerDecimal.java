package fr.siamois.domain.models.form.customfieldanswer.basetypes;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;

import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("DECIMAL")
public class CustomFieldAnswerDecimal extends CustomFieldAnswer {

    @Column(name = "value_as_decimal")
    private Double value;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerDecimal that)) return false;
        if (!super.equals(o)) return false; // Ensures inherited fields are compared

        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Double decimal) {
            this.value = decimal;
        } else {
            throw new IllegalArgumentException("Value must be a Double");
        }
    }
}
