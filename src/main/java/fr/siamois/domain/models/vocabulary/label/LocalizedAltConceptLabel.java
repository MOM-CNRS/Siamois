package fr.siamois.domain.models.vocabulary.label;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity representing an alternative label for a concept in a specific language.
 *
 * @author Julien Linget
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "localized_alt_concept_label")
@Data
public class LocalizedAltConceptLabel extends ConceptLabel {

    public LocalizedAltConceptLabel() {
        super();
    }

    @Override
    public LabelType getLabelType() {
        return LabelType.ALT_LABEL;
    }

}
