package fr.siamois.domain.models.form.customfieldanswer.actionunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import jakarta.persistence.*;

import java.util.List;
import java.util.Objects;

@Entity
public abstract class CustomFieldAnswerActionUnit extends CustomFieldAnswer {

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "custom_field_answer_action_unit_answers",
            joinColumns = {@JoinColumn(name = "fk_custom_field_id", referencedColumnName = "fk_custom_field_id"),
                          @JoinColumn(name = "fk_form_config_answer_id", referencedColumnName = "fk_form_config_answer_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_action_unit_id")})
    protected List<ActionUnit> actionUnits;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CustomFieldAnswerActionUnit that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(actionUnits, that.actionUnits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), actionUnits);
    }
}
