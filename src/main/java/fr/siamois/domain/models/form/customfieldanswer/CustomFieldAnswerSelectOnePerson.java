package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Objects;


/**
 * This class represents a field answer with spatial unit selected
 * @deprecated This column is replaced by {@link CustomFieldAnswerSelectMultipleSpatialUnitTree#value} using
 * custom_field_answer_spatial_unit_answers table with one value in the list
 */
@Data
@Entity
@DiscriminatorValue("SELECT_ONE_PERSON")
@Table(name = "custom_field_answer")
@Deprecated(forRemoval = true, since = "0.13.2-SNAPSHOT")
public class CustomFieldAnswerSelectOnePerson extends CustomFieldAnswerSelectPerson {

    @ManyToOne
    @JoinColumn(name = "fk_value_as_person")
    private Person value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerSelectOnePerson that)) return false;
        if (!super.equals(o)) return false; // Ensures any inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
