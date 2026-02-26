package fr.siamois.dto.entity;

import lombok.Data;

@Data
public class SpecimenDTO extends AbstractEntityDTO {

    private String identifier;
    private ConceptDTO type;
    private String fullIdentifier;

}
