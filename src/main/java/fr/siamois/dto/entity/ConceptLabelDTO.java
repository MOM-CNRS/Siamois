package fr.siamois.dto.entity;

import fr.siamois.domain.models.vocabulary.label.LabelType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class ConceptLabelDTO extends AbstractEntityDTO implements Serializable {

    protected ConceptDTO concept;
    protected String label;
    protected String langCode;
    protected VocabularyDTO vocabulary;
    protected ConceptDTO parentConcept;

    public abstract LabelType getLabelType();

    public boolean isAltLabel() {
        return getLabelType() == LabelType.ALT_LABEL;
    }

}
