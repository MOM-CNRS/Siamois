package fr.siamois.domain.models.form.customfieldanswer.person;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Objects;


@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue("SELECT_ONE_PERSON")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerSelectOnePerson extends CustomFieldAnswerSelectPerson {
    @Override
    public Object getValue() {
        if (Objects.nonNull(persons) && !persons.isEmpty()) {
            return persons.get(0);
        }
        return null;
    }

    @Override
    public void setValue(Object value) {
        if (Objects.isNull(persons)) persons = new ArrayList<>();
        persons.clear();
        if (Objects.isNull(value)) return;
        if (value instanceof Person person) {
            persons.add(0, person);
        } else {
            throw new IllegalArgumentException("Invalid value for custom field answer select person");
        }
    }


}
