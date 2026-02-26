package fr.siamois.dto.entity;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class ActionUnitDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO type;
    private String identifier;
    private SpatialUnitDTO mainLocation ;
    private String fullIdentifier;
    private Set<SpatialUnitDTO> spatialContext = new HashSet<>();

}
