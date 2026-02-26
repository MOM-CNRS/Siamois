package fr.siamois.dto.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SpatialUnitDTO extends AbstractEntityDTO {

    public SpatialUnitDTO (SpatialUnitDTO spatialUnitDTO) {
        new SpatialUnitDTO();
    }

    private String name;
    private ConceptDTO category;

}
