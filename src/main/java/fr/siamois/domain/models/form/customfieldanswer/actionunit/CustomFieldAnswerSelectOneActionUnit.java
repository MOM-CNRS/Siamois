package fr.siamois.domain.models.form.customfieldanswer.actionunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Objects;


@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue("SELECT_ONE_ACTION_UNIT")
public class CustomFieldAnswerSelectOneActionUnit extends CustomFieldAnswerActionUnit {

    @Override
    public Object getValue() {
        if (Objects.isNull(actionUnits) || actionUnits.isEmpty()) {
            return null;
        }
        return actionUnits.get(0);
    }

    @Override
    public void setValue(Object value) {
        if (Objects.isNull(actionUnits)) actionUnits = new ArrayList<>();
        actionUnits.clear();
        if (value instanceof ActionUnit actionUnit) {
            actionUnits.add(actionUnit);
        } else {
            throw new IllegalArgumentException("Invalid value passed to action unit selection");
        }
    }
}
