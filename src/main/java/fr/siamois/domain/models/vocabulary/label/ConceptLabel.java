package fr.siamois.domain.models.vocabulary.label;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

import java.util.Objects;

@Entity
@DiscriminatorValue("concept")
@Data
@Deprecated(forRemoval = true)
public class ConceptLabel extends Label {

    @ManyToOne
    @JoinColumn(name = "fk_concept_id")
    private Concept concept;

    public ConceptLabel() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConceptLabel cl)) return false;

        return Objects.equals(concept, cl.concept) &&
                Objects.equals(value, cl.value)&&
                Objects.equals(langCode, cl.langCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(concept, value, langCode);
    }

}
