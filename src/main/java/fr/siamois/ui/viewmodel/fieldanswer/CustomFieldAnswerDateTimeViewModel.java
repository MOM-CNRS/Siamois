package fr.siamois.ui.viewmodel.fieldanswer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerDateTimeViewModel extends CustomFieldAnswerViewModel {
    private LocalDateTime value;
}
