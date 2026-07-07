package fr.siamois.dto.entity;

import fr.siamois.domain.models.permissions.PermissionScopeType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class ProfileDTO extends AbstractEntityDTO {

    private String code;
    private String name;
    private PermissionScopeType scope;
    private Set<PermissionDTO> permissions;

}