package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOneRecordingUnitViewModel extends CustomFieldAnswerViewModel {
    private RecordingUnitSummaryDTO value;
}
