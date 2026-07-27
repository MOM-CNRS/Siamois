package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.actionunit.ActionUnit;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("SELECT_ONE_ACTION_UNIT")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerSelectOneActionUnit extends CustomFieldAnswerLegacy {

    @ManyToOne
    @JoinColumn(name = "fk_value_as_action_unit")
    private ActionUnit value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerSelectOneActionUnit that)) return false;
        if (!super.equals(o)) return false; // Ensures any inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
