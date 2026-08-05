package fr.siamois.dto.entity.vocabulary;

import lombok.Data;

import java.util.Set;

@Data
public class ConceptCollectionDTO {
    private Long id;
    private String externalId;
    private VocabularyDTO vocabulary;
    private Set<ConceptDTO> concepts;
}
