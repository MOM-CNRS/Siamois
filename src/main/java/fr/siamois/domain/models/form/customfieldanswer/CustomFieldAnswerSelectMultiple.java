package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("SELECT_MULTIPLE")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerSelectMultiple extends CustomFieldAnswerLegacy {

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "custom_field_answer_concept_answers",
            joinColumns = {
                    @JoinColumn(name = "fk_field_id", referencedColumnName = "fk_field_id"),
                    @JoinColumn(name = "fk_form_response", referencedColumnName = "fk_form_response")
            },
            inverseJoinColumns = { @JoinColumn(name = "fk_concept") }
    )
    private List<Concept> value = new ArrayList<>();

    /**
     * Adds a concept to the list if it doesn't already exist
     *
     * @param concept The concept to add
     */
    public void addConcept(Concept concept) {

        if (!value.contains(concept)) {
            value.add(concept);
        }
    }

    /**
     * Removes a concept from the list if it exists
     *
     * @param concept The concept to remove
     */
    public void removeConcept(Concept concept) {
        value.remove(concept);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerSelectMultiple that)) return false;
        if (!super.equals(o)) return false; // Ensures any inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
