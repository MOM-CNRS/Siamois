package fr.siamois.dto.entity;

import fr.siamois.domain.models.vocabulary.VocabularyType;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class VocabularyDTO extends AbstractEntityDTO{


    private VocabularyType type;
    private String externalVocabularyId;
    private String baseUri;

}
