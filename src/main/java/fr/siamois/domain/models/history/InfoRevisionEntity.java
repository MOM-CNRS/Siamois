package fr.siamois.domain.models.history;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.events.listener.InfoRevisionListener;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Custom revision entity to store additional information about revisions.
 * Note on Revtype : 0 -> ADD, 1 -> UPDATE, 2 -> DELETE
 * @author Julien Linget
 */
@Entity
@RevisionEntity
@EqualsAndHashCode
@Data
@Table(name = "revinfo")
@EntityListeners(InfoRevisionListener.class)
public class InfoRevisionEntity {

    @Id
    @GeneratedValue
    @RevisionNumber
    private long revId;

    @RevisionTimestamp
    private long epochTimestamp;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_person_id", nullable = false)
    @JsonIgnore
    protected Person updatedBy;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIgnore
    @JoinColumn(name = "fk_institution_id", nullable = false)
    protected Institution updatedFrom;


}
