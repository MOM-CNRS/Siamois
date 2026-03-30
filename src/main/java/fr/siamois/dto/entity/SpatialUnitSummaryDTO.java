package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@EqualsAndHashCode
public class SpatialUnitSummaryDTO implements Serializable {

    private String name;
    private ConceptDTO category;
    private Long id;

    public SpatialUnitSummaryDTO(SpatialUnitDTO spatialUnitDTO) {
        this.id = spatialUnitDTO.getId();
        this.name = spatialUnitDTO.getName();
        this.category = spatialUnitDTO.getCategory();
    }

}
