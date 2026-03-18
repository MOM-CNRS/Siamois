package fr.siamois.dto.entity;

import fr.siamois.domain.models.actionunit.ActionCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = false)
public class ActionCodeDTO extends AbstractEntityDTO {

    private String code;
    private ConceptDTO type;

}
