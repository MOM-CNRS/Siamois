package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOneActionUnitViewModel extends CustomFieldAnswerViewModel {
    private ActionUnitSummaryDTO value;
}
