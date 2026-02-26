package fr.siamois.dto.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class SpatialUnitSummaryDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO category;

}
