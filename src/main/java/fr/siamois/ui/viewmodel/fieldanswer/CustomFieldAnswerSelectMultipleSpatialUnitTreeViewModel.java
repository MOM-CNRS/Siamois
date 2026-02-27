package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.SpatialUnitDTO;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel extends CustomFieldAnswerViewModel implements Serializable {
    private Set<SpatialUnitDTO> value = new HashSet<>();
}
