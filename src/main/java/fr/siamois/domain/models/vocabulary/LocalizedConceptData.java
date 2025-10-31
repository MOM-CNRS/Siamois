package fr.siamois.domain.models.vocabulary;

import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.LabelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity representing localized data for a concept, including its definition.
 * May be extended in the future to include additional fieds such as notes, examples, etc.
 *
 * @author Julien Linget
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "localized_concept_data")
public class LocalizedConceptData extends ConceptLabel {

    @Column(name = "concept_definition", length = Integer.MAX_VALUE)
    private String definition;

    public LocalizedConceptData() {
        super();
    }

    public void setConcept(Concept concept) {
        this.concept = concept;
        if (concept != null) {
            this.id.setConceptId(concept.getId());
        } else {
            this.id.setConceptId(null);
        }
    }

    @Override
    public LabelType getLabelType() {
        return LabelType.PREF_LABEL;
    }
}
