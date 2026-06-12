package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.entity.SpecimenSummaryDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerSelectMultipleSpecimenViewModel extends CustomFieldAnswerViewModel implements Serializable {
    private List<SpecimenSummaryDTO> value = new ArrayList<>();
}
