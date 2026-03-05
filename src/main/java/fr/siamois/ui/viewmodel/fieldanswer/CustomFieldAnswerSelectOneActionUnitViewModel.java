package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.ActionUnitDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOneActionUnitViewModel extends CustomFieldAnswerViewModel {
    private ActionUnitDTO value;
}
