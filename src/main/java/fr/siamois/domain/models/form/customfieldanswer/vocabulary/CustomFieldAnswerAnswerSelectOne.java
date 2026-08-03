package fr.siamois.domain.models.form.customfieldanswer.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("SELECT_ONE")
public class CustomFieldAnswerAnswerSelectOne extends CustomFieldAnswerSelectConcept {

    @Override
    public Object getValue() {
        if (Objects.isNull(concepts) || concepts.isEmpty()) return null;
        return concepts.get(0);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setValue(Object value) {
        if (Objects.isNull(concepts)) concepts = new ArrayList<>();
        concepts.clear();
        if (Objects.isNull(value)) return;
        if (value instanceof Concept concept) {
            concepts.add(0, concept);
        } else if (value instanceof Collection collection) {
            concepts.addAll(collection);
        } else {
            throw new IllegalArgumentException("Invalid value for custom field answer select concept");
        }
    }
}
