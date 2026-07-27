package fr.siamois.domain.models.form.customfieldanswer.vocabulary;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;

import java.util.List;

@Entity
public abstract class CustomFieldSelectConcept extends CustomFieldAnswer {

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "custom_field_answer_concept_answers",
            joinColumns = @JoinColumn(name = "fk_field_answer_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_concept_id"))
    protected List<Concept> concepts;

}
