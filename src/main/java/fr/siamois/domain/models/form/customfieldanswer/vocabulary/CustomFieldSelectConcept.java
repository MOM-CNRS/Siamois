package fr.siamois.domain.models.form.customfieldanswer.vocabulary;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;

import java.util.List;
import java.util.Objects;

@Entity
public abstract class CustomFieldSelectConcept extends CustomFieldAnswer {

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "custom_field_answer_concept_answers",
            joinColumns = {@JoinColumn(name = "fk_custom_field_id", referencedColumnName = "fk_custom_field_id"),
                          @JoinColumn(name = "fk_form_config_answer_id", referencedColumnName = "fk_form_config_answer_id")},
            inverseJoinColumns = @JoinColumn(name = "fk_concept_id"))
    protected List<Concept> concepts;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CustomFieldSelectConcept that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(concepts, that.concepts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), concepts);
    }
}
