package fr.siamois.domain.models.specimen;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;


@EqualsAndHashCode(callSuper = true)
@Data
@MappedSuperclass
@Audited
public abstract class SpecimenStudyParent extends TraceableEntity {

    @Column(name = "study_date")
    protected OffsetDateTime studyDate;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_ark_id", nullable = false)
    protected Ark ark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_method")
    protected Concept method;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_action_unit_id")
    protected ActionUnit actionUnit;


}
