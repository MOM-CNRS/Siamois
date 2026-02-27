package fr.siamois.dto.entity;

import fr.siamois.domain.models.vocabulary.VocabularyType;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class VocabularyDTO extends AbstractEntityDTO implements Serializable {

    private VocabularyType type;
    private String externalVocabularyId;
    private String baseUri;

}
