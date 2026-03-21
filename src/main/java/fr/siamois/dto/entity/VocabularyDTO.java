package fr.siamois.dto.entity;

import fr.siamois.domain.models.vocabulary.VocabularyType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
@EqualsAndHashCode
public class VocabularyDTO implements Serializable {

    private long id;
    private VocabularyType type;
    private String externalVocabularyId;
    private String baseUri;

}
