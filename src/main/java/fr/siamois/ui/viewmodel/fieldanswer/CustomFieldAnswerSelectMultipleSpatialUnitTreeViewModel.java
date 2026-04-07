package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel extends CustomFieldAnswerViewModel implements Serializable {
    private List<PlaceSuggestionDTO> value = new ArrayList<>();

    private String newName ;
    private ConceptAutocompleteDTO newType ;

    private String source; // source for external data

    public CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel(String source) {
        this.source = source;
    }

}
