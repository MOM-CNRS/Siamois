package fr.siamois.domain.models.vocabulary.label;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity representing an alternative label for a concept in a specific language.
 *
 * @author Julien Linget
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@DiscriminatorValue("1")
public class ConceptAltLabel extends ConceptLabel {

    public ConceptAltLabel() {
        super();
    }

}
