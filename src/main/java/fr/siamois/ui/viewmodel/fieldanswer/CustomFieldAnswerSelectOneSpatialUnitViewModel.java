package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOneSpatialUnitViewModel extends CustomFieldAnswerViewModel {
    private SpatialUnitSummaryDTO value;
}
