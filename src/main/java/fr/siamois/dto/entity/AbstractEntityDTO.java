package fr.siamois.dto.entity;

import fr.siamois.domain.models.auth.Person;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public abstract class AbstractEntityDTO implements Serializable {

    protected Long id;
    protected PersonDTO createdBy;
    protected InstitutionDTO createdByInstitution;
    protected Boolean validated;
    protected OffsetDateTime creationTime;
    protected Person validatedBy ;

}
