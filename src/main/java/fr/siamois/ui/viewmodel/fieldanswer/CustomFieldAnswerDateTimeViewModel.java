package fr.siamois.ui.viewmodel.fieldanswer;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CustomFieldAnswerDateTimeViewModel extends CustomFieldAnswerViewModel {
    private LocalDateTime value;
}
