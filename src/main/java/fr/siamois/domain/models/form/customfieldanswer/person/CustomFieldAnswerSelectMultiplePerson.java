package fr.siamois.domain.models.form.customfieldanswer.person;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;


@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_PERSON")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerSelectMultiplePerson extends CustomFieldAnswerSelectPerson {

    @Override
    public Object getValue() {
        return persons;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setValue(Object value) {
        if (Objects.isNull(persons)) persons = new ArrayList<>();
        persons.clear();
        if (value instanceof Person person) {
            persons.add(person);
        } else if (value instanceof Collection collection) {
            persons.addAll(collection);
        } else {
            throw new IllegalArgumentException("value is not of type Person or Collection");
        }
    }
}
