package fr.siamois.domain.models.vocabulary.label;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

import java.util.Objects;

/**
 * @deprecated Use {@link fr.siamois.domain.models.vocabulary.LocalizedConceptData} as a replacement for ConceptLabel Pref-label.
 * This class is maintained only for backward compatibility and will be removed in future releases.
 * Some query still depend on it.
 */
@Entity
@DiscriminatorValue("concept")
@Data
@Deprecated(forRemoval = true, since = "0.1.2-DEV")
public class ConceptLabelLegacy extends Label {

    @ManyToOne
    @JoinColumn(name = "fk_concept_id")
    private Concept concept;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConceptLabelLegacy that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(concept, that.concept) && super.equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), concept);
    }
}
