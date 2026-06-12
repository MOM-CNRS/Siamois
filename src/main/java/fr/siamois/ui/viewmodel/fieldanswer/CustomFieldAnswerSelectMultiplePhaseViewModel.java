package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class CustomFieldAnswerSelectMultiplePhaseViewModel extends CustomFieldAnswerViewModel {

    private List<PhaseDTO> value = new ArrayList<>();

    // Fields for inline creation
    private String newIdentifier;
    private String newTitle;
    private Integer newOrderNumber;
    private ConceptAutocompleteDTO newType;
}
