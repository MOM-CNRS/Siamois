package fr.siamois.domain.models.form.customfieldanswer.actionunit;

import fr.siamois.domain.models.actionunit.ActionCode;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Objects;


@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue("SELECT_ONE_ACTION_CODE")
public class CustomFieldAnswerSelectOneActionCode extends CustomFieldAnswerActionCode {

    @Override
    public Object getValue() {
        if (Objects.isNull(actionCodes) || actionCodes.isEmpty()) {
            return null;
        }
        return actionCodes.get(0);
    }

    @Override
    public void setValue(Object value) {
        if (Objects.isNull(actionCodes)) actionCodes = new ArrayList<>();
        actionCodes.clear();
        if (value instanceof ActionCode actionCode) {
            actionCodes.add(actionCode);
        } else {
            throw new IllegalArgumentException("Invalid value passed to action code selection");
        }
    }
}
