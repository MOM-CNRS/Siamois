package fr.siamois.domain.models.form.customfieldanswer.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;

import java.util.ArrayList;
import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("SELECT_MULTIPLE")
public class CustomFieldAnswerSelectMultiple extends CustomFieldSelectConcept {

    /**
     * Adds a concept to the list if it doesn't already exist
     *
     * @param concept The concept to add
     */
    public void addConcept(Concept concept) {
        if (Objects.isNull(concepts)) concepts = new ArrayList<>();

        if (Objects.nonNull(concept) && !concepts.contains(concept)) {
            concepts.add(concept);
        }
    }

    /**
     * Removes a concept from the list if it exists
     *
     * @param concept The concept to remove
     */
    public void removeConcept(Concept concept) {
        if (Objects.nonNull(concepts)) {
            concepts.remove(concept);
        }
    }


    @Override
    public Object getValue() {
        return concepts;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setValue(Object value) {
        this.concepts = (ArrayList<Concept>) value;
    }
}
