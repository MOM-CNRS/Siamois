package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.PersonDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOnePersonViewModel extends CustomFieldAnswerSelectPersonViewModel {
    private PersonDTO value;
}
