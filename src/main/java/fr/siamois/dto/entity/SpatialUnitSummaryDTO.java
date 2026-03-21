package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SpatialUnitSummaryDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO category;

    public SpatialUnitSummaryDTO(SpatialUnitDTO spatialUnitDTO) {
        this.id = spatialUnitDTO.getId();
        this.name = spatialUnitDTO.getName();
        this.category = spatialUnitDTO.getCategory();
    }

}
