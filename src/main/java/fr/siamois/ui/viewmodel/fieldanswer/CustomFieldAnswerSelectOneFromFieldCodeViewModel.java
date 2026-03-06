package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOneFromFieldCodeViewModel extends CustomFieldAnswerViewModel {
    private ConceptAutocompleteDTO value;
}
