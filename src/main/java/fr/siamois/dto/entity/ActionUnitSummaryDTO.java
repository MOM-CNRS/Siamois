package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@RequiredArgsConstructor
public class ActionUnitSummaryDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO type;
    private String identifier;
    private String fullIdentifier;
    protected Integer maxRecordingUnitCode;
    protected Integer minRecordingUnitCode;
    private OffsetDateTime beginDate;
    private OffsetDateTime endDate;
    private String recordingUnitIdentifierFormat;

    public ActionUnitSummaryDTO(ActionUnitDTO dto) {
        super(dto);
        this.id = dto.getId();
        this.name = dto.getName();
    }


}
