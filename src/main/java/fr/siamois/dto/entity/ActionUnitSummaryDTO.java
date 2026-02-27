package fr.siamois.dto.entity;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@NoArgsConstructor
public class ActionUnitSummaryDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO type;
    private String identifier;
    private SpatialUnitSummaryDTO mainLocation ;
    private String fullIdentifier;
    protected Integer maxRecordingUnitCode;
    protected Integer minRecordingUnitCode;
    private OffsetDateTime beginDate;
    private OffsetDateTime endDate;
    private String recordingUnitIdentifierFormat;

    public ActionUnitSummaryDTO(ActionUnitDTO dto) {
        this.id = dto.getId();
    }


}
