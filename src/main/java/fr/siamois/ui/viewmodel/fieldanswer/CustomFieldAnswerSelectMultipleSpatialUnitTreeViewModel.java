package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
