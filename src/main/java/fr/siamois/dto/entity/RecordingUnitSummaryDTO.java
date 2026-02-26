package fr.siamois.dto.entity;

import lombok.Data;

import java.util.Set;

@Data
public class RecordingUnitSummaryDTO extends AbstractEntityDTO {

    private String identifier;
    private String fullIdentifier;
    private ConceptDTO type;

    public RecordingUnitSummaryDTO(RecordingUnitSummaryDTO original) {
        identifier = original.getIdentifier();
        fullIdentifier = original.getFullIdentifier();
        type = original.getType();
    }

}
