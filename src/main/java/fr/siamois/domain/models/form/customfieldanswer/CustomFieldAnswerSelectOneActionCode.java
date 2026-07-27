package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.actionunit.ActionCode;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("SELECT_ONE_ACTION_CODE")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerSelectOneActionCode extends CustomFieldAnswerLegacy {

    @ManyToOne
    @JoinColumn(name = "fk_value_as_action_code")
    private ActionCode value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerSelectOneActionCode that)) return false;
        if (!super.equals(o)) return false; // Ensures any inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
