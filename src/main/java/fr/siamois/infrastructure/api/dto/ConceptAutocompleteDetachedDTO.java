package fr.siamois.infrastructure.api.dto;

import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


/**
 * An autocomplete result of a concept that is not imported in the local database, and therefore has
 * no local id : it is identified by the external id of its concept and the URI of its vocabulary.
 * The hierarchy is always empty, since a remote autocomplete does not expose the ancestors of a concept.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ConceptAutocompleteDetachedDTO extends ConceptAutocompleteDTO {

    private final String vocabularyUri;

    public ConceptAutocompleteDetachedDTO(ConceptLabelDTO conceptLabelToDisplay, String originalPrefLabel, List<String> altLabels, String definition, String vocabularyUri) {
        super(conceptLabelToDisplay, originalPrefLabel, altLabels, definition, "");
        this.vocabularyUri = vocabularyUri;
    }

}
