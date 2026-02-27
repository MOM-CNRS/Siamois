package fr.siamois.dto.entity;

import fr.siamois.domain.models.vocabulary.label.LabelType;

public class ConceptPrefLabelDTO extends ConceptLabelDTO{

    @Override
    public LabelType getLabelType() {
        return LabelType.PREF_LABEL;
    }




}
