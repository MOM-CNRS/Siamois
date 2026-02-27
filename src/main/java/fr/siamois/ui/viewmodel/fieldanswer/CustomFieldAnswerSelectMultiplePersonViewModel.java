package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.auth.Person;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CustomFieldAnswerSelectMultiplePersonViewModel extends CustomFieldAnswerViewModel {
    private List<Person> value = new ArrayList<>();
}
