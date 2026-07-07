package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PermissionDTO extends AbstractEntityDTO {
    private Long id;
    private String code;
}
