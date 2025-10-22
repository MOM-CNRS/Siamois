package fr.siamois.domain.models.recordingunit;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Objects;


/**
 * The common attributes of the history recording unit table and the real recording table.
 *
 * @author Julien Linget
 */
@Data
@MappedSuperclass
public abstract class RecordingUnitParent extends TraceableEntity {

    @NotNull
    @OneToOne(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;

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
    @JoinColumn(name = "fk_normalized_interpretation")
    protected Concept normalizedInterpretation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_third_type")
    protected Concept thirdType;

    @Column(name = "start_date")
    protected OffsetDateTime startDate;

    @Column(name = "end_date")
    protected OffsetDateTime endDate;

    @Column(name = "description")
    protected String description;

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
