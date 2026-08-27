package fr.siamois.domain.models.recordingunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;

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
    @JoinColumn(name = "fk_geomorphological_cycle")
    protected Concept geomorphologicalCycle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_geomorphological_agent")
    protected Concept geomorphologicalAgent;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_normalized_interpretation")
    protected Concept normalizedInterpretation;

    @Column(name = "start_date")
    protected OffsetDateTime openingDate;

    @Column(name = "end_date")
    protected OffsetDateTime closingDate;

    @Column(name = "description", length = 5000)
    protected String description;

    @Column(name = "matrix_composition")
    protected String matrixComposition;

    @Column(name = "matrix_color")
    protected String matrixColor;

    @Column(name = "comments")
    protected String comments;

    @Column(name = "matrix_texture")
    protected String matrixTexture;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_erosion_shape")
    protected Concept erosionShape;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_erosion_orientation")
    protected Concept erosionOrientation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_erosion_profile")
    protected Concept erosionProfile;

    @Column(name = "taq")
    protected Integer taq;

    @Column(name = "tpq")
    protected Integer tpq;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_chronological_attribution")
    protected Concept chronologicalAttribution;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_action_unit_id", nullable = false)
    @JsonIgnore
    protected ActionUnit actionUnit;

    @Column(name = "identifier")
    protected Integer identifier;

    @Column(name = "full_identifier")
    protected String fullIdentifier;

    @Embedded
    protected RecordingUnitSize size;

    @Embedded
    protected RecordingUnitAltimetry altitude;

    @ManyToOne
    @JoinColumn(name="fk_spatial_unit_id")
    @JsonIgnore
    protected SpatialUnit spatialUnit;


    /**
     * Business equality: a recording unit is identified by its full identifier <em>within its
     * action unit</em>. The identifier alone is not unique — two action units can each hold a
     * "US 1" — so both halves of the natural key are required.
     * <p>
     * Accessors (not fields) are used throughout so Hibernate proxies compare correctly, and
     * {@code instanceof} rather than {@code getClass()} equality so a proxy can equal its entity.
     * When the natural key is not fully populated — a unit that has not been assigned its real
     * identifier yet — equality falls back to the surrogate id, and transient instances with
     * neither are equal only to themselves.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecordingUnitParent that)) return false;

        String thisFullIdentifier = getFullIdentifier();
        String thatFullIdentifier = that.getFullIdentifier();
        Long thisActionUnitId = getActionUnit() == null ? null : getActionUnit().getId();
        Long thatActionUnitId = that.getActionUnit() == null ? null : that.getActionUnit().getId();

        if (thisFullIdentifier != null && thatFullIdentifier != null
                && thisActionUnitId != null && thatActionUnitId != null) {
            return thisFullIdentifier.equals(thatFullIdentifier)
                    && thisActionUnitId.equals(thatActionUnitId);
        }

        return getId() != null && Objects.equals(getId(), that.getId());
    }

    /**
     * Deliberately constant, and deliberately NOT derived from the natural key.
     * <p>
     * {@code fullIdentifier} is mutable: a created or duplicated unit is first saved with a
     * temporary placeholder and only afterwards has its real identifier generated and written back
     * in place (see {@code RecordingUnitService#generateFullIdentifier}). By then the entity may
     * already sit in a {@code HashSet} — {@code parents}/{@code children} are exactly that — and a
     * hash that changes under a live element strands it in the wrong bucket, which is what broke
     * structure duplication. A constant keeps every instance in one bucket, so mutation stays safe
     * and {@code equals} alone decides identity. It also keeps a Hibernate proxy and its entity in
     * the same bucket, which {@code getClass().hashCode()} would not.
     */
    @Override
    public int hashCode() {
        return RecordingUnitParent.class.hashCode();
    }
}
