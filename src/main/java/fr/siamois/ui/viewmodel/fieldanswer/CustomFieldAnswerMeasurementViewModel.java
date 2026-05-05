package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.MeasurementAnswerDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerMeasurementViewModel extends CustomFieldAnswerViewModel {
    private MeasurementAnswerDTO value;
}
