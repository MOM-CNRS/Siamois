package fr.siamois.domain.models.form.customfieldanswer.actionunit;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import jakarta.persistence.*;

import java.util.List;

@Entity
public abstract class CustomFieldAnswerActionCode extends CustomFieldAnswer {

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "custom_field_answer_action_code_answers",
            joinColumns = {@JoinColumn(name = "fk_field_answer_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_action_code_id")})
    protected List<ActionCode> actionCodes;

}
