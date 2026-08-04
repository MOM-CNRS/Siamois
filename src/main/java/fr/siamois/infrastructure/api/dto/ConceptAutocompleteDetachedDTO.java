package fr.siamois.infrastructure.api.dto;

import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
public class ConceptAutocompleteDetachedDTO extends ConceptAutocompleteDTO {

    private final String vocabularyUri;

    public ConceptAutocompleteDetachedDTO(ConceptLabelDTO conceptLabelToDisplay, String originalPrefLabel, List<String> altLabels, String vocabularyUri) {
        super(conceptLabelToDisplay, originalPrefLabel, altLabels, "", "");
        this.vocabularyUri = vocabularyUri;
    }

}
