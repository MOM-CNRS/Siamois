package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
public class CustomFieldAnswerSelectMultipleContainerViewModel extends CustomFieldAnswerViewModel {

    private List<ContainerDTO> value = new ArrayList<>();

    // To create new :
    private String newIdentifier;
    private ConceptAutocompleteDTO newType;

}
