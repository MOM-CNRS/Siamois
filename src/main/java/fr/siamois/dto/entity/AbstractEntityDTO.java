package fr.siamois.dto.entity;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public abstract class AbstractEntityDTO {

    protected Long id;
    protected PersonDTO createdBy;
    protected InstitutionDTO createdByInstitution;
    private Boolean validated;

}
