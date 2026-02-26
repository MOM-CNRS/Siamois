package fr.siamois.dto.entity;

import lombok.Data;

import java.util.Set;

@Data
public class RecordingUnitDTO extends AbstractEntityDTO {

    private String identifier;
    private String fullIdentifier;
    private ConceptDTO type;
    private ActionUnitDTO actionUnit;
    private Set<RecordingUnitSummaryDTO> parents;

    public RecordingUnitDTO(RecordingUnitDTO original) {
        identifier = original.getIdentifier();
        fullIdentifier = original.getFullIdentifier();
        type = original.getType();
        actionUnit = original.getActionUnit();
    }

    /**
     * Resets the full identifier to it's base format.
     */
    public void resetFullIdentifier() {
        if (actionUnit == null) return;
        fullIdentifier = actionUnit.getFullIdentifier();
    }
}
