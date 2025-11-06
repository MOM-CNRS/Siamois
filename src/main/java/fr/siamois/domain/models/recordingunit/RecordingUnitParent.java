package fr.siamois.domain.models.recordingunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.OffsetDateTime;
import java.util.Objects;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;


/**
 * The common attributes of the history recording unit table and the real recording table.
 *
 * @author Julien Linget
 */
@Data
@MappedSuperclass
@Audited
public abstract class RecordingUnitParent extends TraceableEntity {

    @NotNull
    @OneToOne(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_author_id", nullable = false)
    @JsonIgnore
    @Audited(targetAuditMode = NOT_AUDITED)
    protected Person author;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_type")
    protected Concept type;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_secondary_type")
    protected Concept secondaryType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_geomorphological_cycle")
    protected Concept geomorphologicalCycle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_geomorphological_agent")
    protected Concept geomorphologicalAgent;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_normalized_interpretation")
    protected Concept normalizedInterpretation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_third_type")
    protected Concept thirdType;

    @Column(name = "start_date")
    protected OffsetDateTime openingDate;

    @Column(name = "end_date")
    protected OffsetDateTime closingDate;

    @Column(name = "description")
    protected String description;

    @Column(name = "matrix_composition")
    protected String matrixComposition;

    @Column(name = "matrix_color")
    protected String matrixColor;

    @Column(name = "matrix_texture")
    protected String matrixTexture;

    @Column(name = "erosion_shape")
    protected String erosionShape;

    @Column(name = "erosion_orientation")
    protected String erosionOrientation;

    @Column(name = "erosion_profile")
    protected String erosionProfile;

    @Column(name = "taq")
    protected Integer taq;

    @Column(name = "tpq")
    protected Integer tpq;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_chronological_phase")
    protected Concept chronologicalPhase;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_action_unit_id", nullable = false)
    protected ActionUnit actionUnit;

    @NotNull
    @Column(name = "identifier")
    protected Integer identifier;

    @NotNull
    @Column(name = "full_identifier")
    protected String fullIdentifier;

    @Embedded
    protected RecordingUnitSize size;

    @Embedded
    protected RecordingUnitAltimetry altitude;

    @OneToOne(
            orphanRemoval=true,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    )
    @JoinColumn(name = "fk_custom_form_response", referencedColumnName = "custom_form_response_id")
    @NotAudited
    protected CustomFormResponse formResponse;

    @ManyToOne
    @JoinColumn(name="fk_spatial_unit_id")
    protected SpatialUnit spatialUnit;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RecordingUnitParent that)) return false;
        return Objects.equals(fullIdentifier, that.fullIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fullIdentifier);
    }
}
