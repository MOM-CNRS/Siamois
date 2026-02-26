package fr.siamois.dto.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class RecordingUnitSummaryDTO extends AbstractEntityDTO {

    private String identifier;
    private String fullIdentifier;
    private ConceptDTO type;

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
