package fr.siamois.domain.models.form.customfieldanswer;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("INTEGER")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerInteger extends CustomFieldAnswer {

    @Column(name = "value_as_integer")
    private Integer value;



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerInteger that)) return false;
        if (!super.equals(o)) return false; // Ensures inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
