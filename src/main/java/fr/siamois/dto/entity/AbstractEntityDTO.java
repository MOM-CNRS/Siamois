package fr.siamois.dto.entity;

import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.auth.Person;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class AbstractEntityDTO implements Serializable {

    AbstractEntityDTO(AbstractEntityDTO dto) {
        id = dto.getId();
        createdBy = dto.getCreatedBy();
        createdByInstitution = dto.getCreatedByInstitution();
        validated = dto.getValidated();
        creationTime = dto.getCreationTime();
        validatedBy = dto.getValidatedBy();
        validatedAt = dto.getValidatedAt();
    }

    @EqualsAndHashCode.Include
    protected Long id;
    protected PersonDTO createdBy;
    protected InstitutionDTO createdByInstitution;
    protected ValidationStatus validated = ValidationStatus.INCOMPLETE;
    protected OffsetDateTime creationTime = OffsetDateTime.now(ZoneId.systemDefault());
    protected OffsetDateTime validatedAt ;
    protected Person validatedBy ;

}
