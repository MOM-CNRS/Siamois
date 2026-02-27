package fr.siamois.dto.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class ConceptDTO extends AbstractEntityDTO implements Serializable {

    private String externalId;
    private VocabularyDTO vocabulary;
    private boolean deleted;

}
