package fr.siamois.dto.entity;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class ActionUnitSummaryDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO type;
    private String identifier;
    private SpatialUnitDTO mainLocation ;
    private String fullIdentifier;
    private Set<SpatialUnitSummaryDTO> spatialContext = new HashSet<>();
    protected Integer maxRecordingUnitCode;
    protected Integer minRecordingUnitCode;


}
