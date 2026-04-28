package fr.siamois.dto.entity;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.document.Document;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
public class ActionUnitDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO type;
    private String identifier;
    private String recordingUnitIdentifierFormat;
    private SpatialUnitSummaryDTO mainLocation ;
    private String fullIdentifier;
    private Set<SpatialUnitSummaryDTO> spatialContext = new HashSet<>();
    private Integer maxRecordingUnitCode=999;
    private Integer minRecordingUnitCode=1;
    private Set<ActionUnitSummaryDTO> children;
    private Set<ActionUnitSummaryDTO> parents;
    private Set<RecordingUnitSummaryDTO> recordingUnitList;
    private Set<Document> documents;
    private String recordingUnitIdentifierLang;
    private OffsetDateTime beginDate;
    private OffsetDateTime endDate;
    private ActionCodeDTO primaryActionCode;

    public List<String> getBindableFieldNames() {
        return List.of("type", "name", "identifier", "spatialContext", "beginDate", "endDate", "primaryActionCode", "mainLocation");
    }

}
