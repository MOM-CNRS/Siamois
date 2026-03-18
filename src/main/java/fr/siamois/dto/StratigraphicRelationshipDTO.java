package fr.siamois.dto;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@EqualsAndHashCode
public class StratigraphicRelationshipDTO implements Serializable {

    private RecordingUnitSummaryDTO unit1;
    private RecordingUnitSummaryDTO unit2;
    private ConceptDTO concept;
    private Boolean isAsynchronous;
    private Boolean conceptDirection;
    private Boolean uncertain;

}
