package fr.siamois.domain.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * A {@link TraceableEntity} stores the creation time and the creator of an entity. The {@link TraceableEntity} is most likely to have a history
 * table in the database to store previous versions.
 *
 * @author Julien Linget
 */
@Setter
@Getter
@MappedSuperclass
public abstract class TraceableEntity implements Serializable {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_created_by", nullable = false)
    @JsonIgnore
    protected Person createdBy;

    @ColumnDefault("NOW()")
    @Column(name = "creation_time")
    protected OffsetDateTime creationTime = OffsetDateTime.now(ZoneId.systemDefault());

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIgnore
    @JoinColumn(name = "fk_institution_id", nullable = false)
    protected Institution createdByInstitution;

    @NotNull
    @Column(name = "validated", nullable = false)
    protected Boolean validated = false;

    @Column(name = "validated_at")
    protected OffsetDateTime validatedAt ;

    @ManyToOne
    @JoinColumn(name = "fk_validated_by")
    @JsonIgnore
    protected Person validatedBy ;

    public abstract Long getId();
}
