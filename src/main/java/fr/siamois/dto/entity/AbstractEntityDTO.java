package fr.siamois.dto.entity;

import fr.siamois.domain.models.auth.Person;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public abstract class AbstractEntityDTO implements Serializable {

    AbstractEntityDTO(AbstractEntityDTO dto) {
        id = dto.getId();
        createdBy = dto.getCreatedBy();
        createdByInstitution = dto.getCreatedByInstitution();
        validated = dto.getValidated();
        creationTime = dto.getCreationTime();
        validatedBy = dto.getValidatedBy();
    }

    protected Long id;
    protected PersonDTO createdBy;
    protected InstitutionDTO createdByInstitution;
    protected Boolean validated;
    protected OffsetDateTime creationTime;
    protected OffsetDateTime validatedAt;
    protected Person validatedBy ;

}
