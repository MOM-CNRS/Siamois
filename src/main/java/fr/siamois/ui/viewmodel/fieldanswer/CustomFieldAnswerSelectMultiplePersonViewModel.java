package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.PersonDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectMultiplePersonViewModel extends CustomFieldAnswerViewModel {
    private List<PersonDTO> value = new ArrayList<>();
}
