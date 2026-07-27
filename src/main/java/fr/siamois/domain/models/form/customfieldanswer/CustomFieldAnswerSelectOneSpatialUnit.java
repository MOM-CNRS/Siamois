package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Objects;


/**
 * This class represents a field answer with one concept selected
 * @deprecated This column is replaced by {@link CustomFieldAnswerSelectMultiplePerson#value} using
 * custom_field_answer_person_answers table with one value in the list
 */
@Data
@Entity
@DiscriminatorValue("SELECT_ONE_SPATIAL_UNIT")
@Table(name = "custom_field_answer")
@Deprecated(forRemoval = true, since = "0.13.2-SNAPSHOT")
public class CustomFieldAnswerSelectOneSpatialUnit extends CustomFieldAnswerLegacy {

    @ManyToOne
    @JoinColumn(name = "fk_value_as_spatial_unit")
    private SpatialUnit value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerSelectOneSpatialUnit that)) return false;
        if (!super.equals(o)) return false; // Ensures any inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
