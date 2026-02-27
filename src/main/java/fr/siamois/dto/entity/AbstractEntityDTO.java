package fr.siamois.dto.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Data
public abstract class AbstractEntityDTO {

    protected Long id;
    protected PersonDTO createdBy;
    protected InstitutionDTO createdByInstitution;
    private Boolean validated;
    protected OffsetDateTime creationTime;
    protected Person validatedBy ;

}
