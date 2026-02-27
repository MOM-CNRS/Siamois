package fr.siamois.dto.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class SpatialUnitSummaryDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO category;

    public SpatialUnitSummaryDTO(SpatialUnitDTO spatialUnitDTO) {
        this.name = spatialUnitDTO.getName();
        this.category = spatialUnitDTO.getCategory();
    }

}
