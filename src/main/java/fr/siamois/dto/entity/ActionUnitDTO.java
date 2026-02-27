package fr.siamois.dto.entity;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class ActionUnitDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO type;
    private String identifier;
    private String recordingUnitIdentifierFormat;
    private SpatialUnitSummaryDTO mainLocation ;
    private String fullIdentifier;
    private Set<SpatialUnitSummaryDTO> spatialContext = new HashSet<>();
    protected Integer maxRecordingUnitCode;
    protected Integer minRecordingUnitCode;
    private Set<ActionUnitSummaryDTO> parents;
    private Set<ActionUnitSummaryDTO> children;
    private Set<RecordingUnitSummaryDTO> recordingUnitList;
    protected String recordingUnitIdentifierLang;
    private Boolean validated;
    private OffsetDateTime beginDate;
    private OffsetDateTime endDate;

    public List<String> getBindableFieldNames() {
        return List.of("type", "name", "identifier", "spatialContext", "beginDate", "endDate", "primaryActionCode", "mainLocation");
    }

}
