package fr.siamois.dto.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class SpatialUnitDTO extends AbstractEntityDTO {

    public SpatialUnitDTO (SpatialUnitDTO spatialUnitDTO) {
        new SpatialUnitDTO();
    }

    private String name;
    private ConceptDTO category;
    private Set<SpatialUnitSummaryDTO> parents;
    private Set<SpatialUnitSummaryDTO> children;
    private Set<RecordingUnitSummaryDTO> recordingUnitList;
    private Set<ActionUnitDTO> relatedActionUnitList;


}
