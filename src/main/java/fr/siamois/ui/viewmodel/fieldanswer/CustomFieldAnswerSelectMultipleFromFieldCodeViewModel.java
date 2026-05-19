package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectMultipleFromFieldCodeViewModel extends CustomFieldAnswerViewModel {
    private List<ConceptAutocompleteDTO> value;
}
