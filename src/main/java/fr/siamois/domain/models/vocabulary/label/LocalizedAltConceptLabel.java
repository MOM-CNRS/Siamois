package fr.siamois.domain.models.vocabulary.label;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
