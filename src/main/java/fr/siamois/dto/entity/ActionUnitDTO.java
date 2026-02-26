package fr.siamois.dto.entity;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class ActionUnitDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO type;
    private String identifier;
    private String recordingUnitIdentifierFormat;
    private SpatialUnitDTO mainLocation ;
    private String fullIdentifier;
    private Set<SpatialUnitDTO> spatialContext = new HashSet<>();
    protected Integer maxRecordingUnitCode;
    protected Integer minRecordingUnitCode;
    private Set<ActionUnitSummaryDTO> parents;
    private Set<ActionUnitSummaryDTO> children;
    private Set<RecordingUnitSummaryDTO> recordingUnitList;
    protected String recordingUnitIdentifierLang;

}
