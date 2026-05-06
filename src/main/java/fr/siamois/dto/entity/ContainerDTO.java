package fr.siamois.dto.entity;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ContainerDTO extends AbstractEntityDTO {

    protected Concept type;
    protected SpatialUnit spatialUnit;
    private Long id;
    protected String identifier;

}
