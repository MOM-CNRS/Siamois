package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
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
public class CustomFieldAnswerSelectMultipleRecordingUnitViewModel extends CustomFieldAnswerViewModel implements Serializable {
    private List<RecordingUnitSummaryDTO> value = new ArrayList<>();

    private ConceptAutocompleteDTO newType ;
}
