package fr.siamois.dto.entity;

import fr.siamois.domain.models.vocabulary.label.LabelType;

public class ConceptAltLabelDTO extends ConceptLabelDTO{


    @Override
    public LabelType getLabelType() {
        return LabelType.ALT_LABEL;
    }

}
