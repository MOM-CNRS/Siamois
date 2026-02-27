package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.auth.Person;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CustomFieldAnswerSelectOnePersonViewModel extends CustomFieldAnswerViewModel {
    private Person value;
}
