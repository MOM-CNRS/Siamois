package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerStratigraphyViewModel extends CustomFieldAnswerViewModel {

        // The rels
        private transient Set<StratigraphicRelationshipDTO> anteriorRelationships = new HashSet<>();
        private transient Set<StratigraphicRelationshipDTO> posteriorRelationships = new HashSet<>();
        private transient Set<StratigraphicRelationshipDTO> synchronousRelationships = new HashSet<>();

        // New rel form
        private transient ConceptAutocompleteDTO conceptToAdd;
        private transient RecordingUnitSummaryDTO sourceToAdd = new RecordingUnitSummaryDTO(); // always the recording unit the panel is about
        private transient RecordingUnitSummaryDTO targetToAdd;
        private transient Boolean vocabularyDirectionToAdd = false; // always false in this version
        private transient Boolean isUncertainToAdd;

        // Displayed selected rel info
        private transient StratigraphicRelationshipDTO selectedRel;

}
