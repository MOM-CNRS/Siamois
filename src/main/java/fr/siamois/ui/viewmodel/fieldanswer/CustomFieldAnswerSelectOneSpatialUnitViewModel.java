package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.ConceptLabelDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOneSpatialUnitViewModel extends CustomFieldAnswerViewModel {
    private SpatialUnitSummaryDTO value;

    // To create new :
    private String newName;
    private ConceptAutocompleteDTO newType;
}
