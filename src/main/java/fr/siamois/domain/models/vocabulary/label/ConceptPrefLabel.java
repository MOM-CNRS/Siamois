package fr.siamois.domain.models.vocabulary.label;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@DiscriminatorValue("0")
public class ConceptPrefLabel extends ConceptLabel {
    @Override
    public LabelType getLabelType() {
        return LabelType.PREF_LABEL;
    }
}
