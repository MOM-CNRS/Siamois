package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class ProjectMemberDTO extends InstitutionMemberDTO {
    protected ActionUnitDTO actionUnit;
}
