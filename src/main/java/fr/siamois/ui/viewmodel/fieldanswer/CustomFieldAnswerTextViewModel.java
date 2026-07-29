package fr.siamois.ui.viewmodel.fieldanswer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Setter
@NoArgsConstructor
public class CustomFieldAnswerTextViewModel extends CustomFieldAnswerViewModel {
    private String value;

    @Override
    public String getValue() {
        return value;
    }
}
