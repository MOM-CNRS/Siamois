package fr.siamois.dto.entity;

import lombok.Data;

@Data
public class ConceptDTO extends AbstractEntityDTO {

    private String externalId;
    private VocabularyDTO vocabulary;

}
