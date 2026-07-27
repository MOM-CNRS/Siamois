package fr.siamois.domain.models.form.customfieldanswer.actionunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import jakarta.persistence.*;

import java.util.List;

@Entity
public abstract class CustomFieldAnswerActionUnit extends CustomFieldAnswer {

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "custom_field_answer_action_unit_answers",
            joinColumns = {@JoinColumn(name = "fk_field_answer_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_action_unit_id")})
    protected List<ActionUnit> actionUnits;

}
