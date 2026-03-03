package fr.siamois.dto.entity;

import fr.siamois.dto.StratigraphicRelationshipDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class RecordingUnitSummaryDTO extends AbstractEntityDTO {

    private String identifier;
    private String fullIdentifier;
    private ConceptDTO type;
    private Set<StratigraphicRelationshipDTO> relationshipsAsUnit1 ;
    private Set<StratigraphicRelationshipDTO> relationshipsAsUnit2 ;

    public RecordingUnitSummaryDTO(RecordingUnitSummaryDTO original) {
        identifier = original.getIdentifier();
        fullIdentifier = original.getFullIdentifier();
        type = original.getType();
    }

    public RecordingUnitSummaryDTO(RecordingUnitDTO plain) {
        identifier = plain.getIdentifier();
        fullIdentifier = plain.getFullIdentifier();
        type = plain.getType();
    }

}
