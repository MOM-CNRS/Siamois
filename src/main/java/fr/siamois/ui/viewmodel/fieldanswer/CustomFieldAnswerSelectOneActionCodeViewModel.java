package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.dto.entity.ActionCodeDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOneActionCodeViewModel extends CustomFieldAnswerViewModel {
     private ActionCodeDTO value;
}
