package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
public class CustomFieldAnswerSelectMultipleContainerViewModel extends CustomFieldAnswerViewModel {

    private Set<ContainerDTO> value;

    // To create new :
    private String newIdentifier;
    private ConceptAutocompleteDTO newType;

}
