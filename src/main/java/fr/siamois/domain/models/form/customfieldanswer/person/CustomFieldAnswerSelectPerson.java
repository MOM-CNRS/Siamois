package fr.siamois.domain.models.form.customfieldanswer.person;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;
import java.util.Objects;


@Data
@Entity
public abstract class CustomFieldAnswerSelectPerson extends CustomFieldAnswer {

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "custom_field_answer_person_answers",
            joinColumns = { @JoinColumn(name = "fk_field_answer_id") },
            inverseJoinColumns = { @JoinColumn(name = "fk_person_id") }
    )
    protected List<Person> persons;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CustomFieldAnswerSelectPerson that)) return false;
        return Objects.equals(persons, that.persons);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(persons);
    }
}
