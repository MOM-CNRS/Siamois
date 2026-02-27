package fr.siamois.dto.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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
    private Set<ActionUnitSummaryDTO> relatedActionUnitList;

    public List<String> getBindableFieldNames() {
        return List.of("category", "name");
    }



}
