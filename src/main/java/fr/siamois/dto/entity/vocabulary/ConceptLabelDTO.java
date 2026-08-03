package fr.siamois.dto.entity.vocabulary;

import fr.siamois.domain.models.vocabulary.label.LabelType;
import fr.siamois.dto.entity.AbstractEntityDTO;
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
