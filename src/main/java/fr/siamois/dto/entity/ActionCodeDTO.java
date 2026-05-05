package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ActionCodeDTO extends AbstractEntityDTO {

    private String code;
    private ConceptDTO type;

}
