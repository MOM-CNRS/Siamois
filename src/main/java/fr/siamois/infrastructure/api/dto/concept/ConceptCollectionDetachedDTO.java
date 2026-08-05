package fr.siamois.infrastructure.api.dto.concept;

import fr.siamois.dto.entity.vocabulary.ConceptCollectionDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * A collection of a remote thesaurus, not imported in the local database and therefore carrying no
 * local id nor concepts : only its external id, its vocabulary and its labels.
 * <p>
 * It is a {@link ConceptCollectionDTO}, so a collection picked among these results can be handed over
 * as is to {@code FormConfigService.addConceptConfigFor} to configure a field on it.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ConceptCollectionDetachedDTO extends ConceptCollectionDTO {

    /**
     * The label to show to the user, already resolved in their language.
     */
    private final String labelToDisplay;

    /**
     * Every label the thesaurus holds for that collection, one per language.
     */
    private final List<LabelDTO> labels;

    public ConceptCollectionDetachedDTO(String externalId, VocabularyDTO vocabulary, String labelToDisplay, List<LabelDTO> labels) {
        setExternalId(externalId);
        setVocabulary(vocabulary);
        this.labelToDisplay = labelToDisplay;
        this.labels = labels;
    }

    @Override
    public String toString() {
        return labelToDisplay;
    }

}
