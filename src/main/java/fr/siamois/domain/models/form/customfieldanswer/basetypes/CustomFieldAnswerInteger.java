package fr.siamois.domain.models.form.customfieldanswer.basetypes;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("INTEGER")
public class CustomFieldAnswerInteger extends CustomFieldAnswer {

    @Column(name = "value_as_integer")
    private Integer value;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerInteger that)) return false;
        if (!super.equals(o)) return false; // Ensures inherited fields are compared

        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Integer) {
            this.value = (Integer) value;
        } else {
            throw new IllegalArgumentException("Value must be an Integer");
        }
    }
}
