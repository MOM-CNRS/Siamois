package fr.siamois.dto.entity;

import lombok.Data;

@Data
public class SpecimenDTO extends AbstractEntityDTO {

    private int identifier;
    private ConceptDTO type;
    private String fullIdentifier;

    public SpecimenDTO(SpecimenDTO original) {
    }
}
