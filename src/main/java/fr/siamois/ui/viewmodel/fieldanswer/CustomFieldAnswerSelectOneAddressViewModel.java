package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.FullAddress;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOneAddressViewModel extends CustomFieldAnswerViewModel {
    private FullAddress value;
}
