package fr.siamois.dto.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SpecimenDTO extends AbstractEntityDTO {

    private int identifier;
    private ConceptDTO type;
    private String fullIdentifier;
    private Boolean validated;

    public SpecimenDTO(SpecimenDTO original) {
    }
}
