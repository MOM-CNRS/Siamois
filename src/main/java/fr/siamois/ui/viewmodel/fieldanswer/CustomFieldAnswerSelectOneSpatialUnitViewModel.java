package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.SpatialUnitDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectOneSpatialUnitViewModel extends CustomFieldAnswerViewModel {
    private SpatialUnitDTO value;
}
