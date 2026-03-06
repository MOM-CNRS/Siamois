package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel extends CustomFieldAnswerViewModel implements Serializable {
    private Set<SpatialUnitSummaryDTO> value = new HashSet<>();
}
