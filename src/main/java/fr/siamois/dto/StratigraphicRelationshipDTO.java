package fr.siamois.dto;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StratigraphicRelationshipDTO  {

    private RecordingUnitSummaryDTO unit1;
    private RecordingUnitSummaryDTO unit2;
    private ConceptDTO concept;
    private Boolean isAsynchronous;
    private Boolean conceptDirection;
    private Boolean uncertain;

}
