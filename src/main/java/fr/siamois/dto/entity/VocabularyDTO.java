package fr.siamois.dto.entity;

import fr.siamois.domain.models.vocabulary.VocabularyType;
import lombok.*;

import java.io.Serializable;

@Setter
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyDTO implements Serializable {

    private long id;
    private VocabularyType type;
    private String externalVocabularyId;
    private String baseUri;

}
