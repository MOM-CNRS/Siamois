package fr.siamois.domain.models.vocabulary.label;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@DiscriminatorValue("0")
@NoArgsConstructor
public class ConceptPrefLabel extends ConceptLabel {

    public ConceptPrefLabel(String conceptLabel, String lang) {
        super();
        this.label = conceptLabel;
        this.langCode = lang;
    }

    @Override
    public LabelType getLabelType() {
        return LabelType.PREF_LABEL;
    }
}
