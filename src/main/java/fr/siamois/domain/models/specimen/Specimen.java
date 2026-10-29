package fr.siamois.domain.models.specimen;


import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.domain.models.identifier.ProjectIdentifierConstraints;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldOnTheFly;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Data
@Entity
@Table(name = "specimen", indexes = {
        @Index(columnList = "full_identifier", name = "idx_specimen_full_identifier"),
        @Index(columnList = "fk_institution_id", name = "idx_specimen_institution")
}, uniqueConstraints = @UniqueConstraint(name = ProjectIdentifierConstraints.SPECIMEN,
        columnNames = {"fk_action_unit_id", "full_identifier"}))
@Audited
@NoArgsConstructor
public class Specimen extends TraceableEntity implements ArkEntity {

    @SuppressWarnings("CopyConstructorMissesField")
    public Specimen(@NonNull Specimen specimen) {
        setRecordingUnit(specimen.getRecordingUnit());
        setType(specimen.getType());
        setCreatedByInstitution(specimen.getCreatedByInstitution());
        setCreatedBy(specimen.getCreatedBy());
        setAuthors(specimen.getAuthors());
        setCollectors(specimen.getCollectors());
        setCollectionDate(specimen.getCollectionDate());
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "specimen_id", nullable = false)
    private Long id;

    /** Measurement (and other) fields added on the fly to this record's fiche. */
    @NotAudited
    @JsonIgnore
    @OneToMany
    @JoinTable(
            name = "specimen_on_the_fly_fields",
            joinColumns = { @JoinColumn(name = "fk_specimen_id") },
            inverseJoinColumns = { @JoinColumn(name = "fk_custom_field_id") }
    )
    private Set<CustomFieldOnTheFly> onTheFlyFields = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "specimen_authors",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_person_id"))
    @NotAudited
    private List<Person> authors;

    @ManyToMany
    @JoinTable(
            name = "specimen_collectors",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_person_id"))
    @NotAudited
    private List<Person> collectors;

    /** What a sample is made of ({@code nature_prelevement}); only for the sample type. */
    @FieldCode
    public static final String SAMPLING_NATURE_FIELD = "SIAS.SAMPLINGNATURE";

    @FieldCode
    public static final String SANITARY_ALERT_FIELD = "SIAS.SANITARYALERT";

    /** How a sample is kept ({@code condition_preservation_prelevement}); only for the sample type. */
    @FieldCode
    public static final String PRESERVATION_CONDITION_FIELD = "SIAS.PRESERVATIONCONDITION";

    @FieldCode
    public static final String TYPE_FIELD = "SIAS.CAT"; // lot, individu, echantillon

    @FieldCode
    public static final String MATIERE_FIELD = "SIAS.MATIERE";

    @FieldCode
    public static final String INTERPRETATION_FIELD = "SIAS.INTERPRETATION";

    @FieldCode
    public static final String CLASS_FIELD = "SIAS.CLASS";

    @FieldCode
    public static final String SANITARY_STATE_FIELD = "SIAS.SANITARY";

    @FieldCode
    public static final String CHRONOLOGICAL_ATTRIBUTION_FIELD = "SIAS.CHRONO";

    @FieldCode
    public static final String TRANSFER_STATUS_FIELD = "SIAS.TRANSFER";

    @FieldCode
    public static final String SELECTIVE_CONSERVATION_FIELD = "SIAS.SELECTIVECONSERVATION";

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_specimen_type")
    protected Concept type; // lot, object, echantillon

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_interpretation")
    protected Concept normalizedInterpretation;

    @ManyToMany
    @JoinTable(
            name = "specimen_material",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_material_id"))
    private Set<Concept> material;

    @ManyToMany(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinTable(
            name="specimen_hierarchy",
            joinColumns = { @JoinColumn(name = "fk_parent_id") },
            inverseJoinColumns = { @JoinColumn(name = "fk_child_id") }
    )
    private Set<Specimen> children = new HashSet<>();

    @ManyToMany(mappedBy = "children", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<Specimen> parents = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "specimen_material_class",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_material_class__id"))
    private Set<Concept> materialClass;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_sanitary_state")
    protected Concept sanitaryState;


    @Column(name = "collection_date")
    protected OffsetDateTime collectionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_recording_unit_id", nullable = false)
    protected RecordingUnit recordingUnit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_action_unit_id")
    protected ActionUnit actionUnit;

    @Column(name = "identifier")
    protected Integer identifier;

    @Column(name = "isolat_identifier")
    protected String isolationNumber;

    @Column(name = "taq")
    protected Integer taq;

    @Column(name = "tpq")
    protected Integer tpq;

    @Column(name = "other_identifier")
    protected String otherIdentifier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_chronological_attribution")
    protected Concept chronologicalAttribution;

    @Column(name = "number_of_element")
    protected Integer numberOfElements;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fk_weight")
    @Audited(targetAuditMode = NOT_AUDITED)
    private MeasurementAnswer weight;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "specimen_container",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_container_id")
    )
    @NotAudited
    private Set<Container> containers = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "specimen_phase",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_phase_id")
    )
    @NotAudited
    private Set<Phase> phases = new HashSet<>();

    @NotNull
    @Column(name = "full_identifier")
    protected String fullIdentifier;

    @Column(name = "description", length = 5000)
    protected String description;

    @Column(name = "comments", length = 5000)
    protected String comments;

    @Column(name = "sanitary_alert", length = 5000)
    protected String sanitaryAlert;

    /** Whether the find is handed over to the State (versement). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_transfer_status")
    protected Concept transferStatus;

    /** Whether the find is worth being kept after the handover (conservation sélective). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_selective_conservation")
    protected Concept selectiveConservation;

    @Column(name = "selective_conservation_comment", length = 5000)
    protected String selectiveConservationComment;

    @Column(name = "particular_interest", length = 5000)
    protected String particularInterest;

    /** Anthropobiological remains: whether the skeleton or lot still has a research potential. */
    @Column(name = "study_potential")
    protected Boolean studyPotential;

    @Column(name = "study_potential_comment", length = 5000)
    protected String studyPotentialComment;

    /** Samples: what the sample was taken for. */
    @Column(name = "sampling_purpose", length = 5000)
    protected String samplingPurpose;

    /** Samples: how the sample is kept after the handover. */
    @Column(name = "preservation_condition", length = 5000)
    protected String preservationCondition;

    /** Samples: from this date the State may dispose of the sample. */
    @Column(name = "validity_date")
    protected OffsetDateTime validityDate;

    /** Where the find was found: any geometry (usually a point). */
    @Column(name = "geom", columnDefinition = "geometry")
    @JsonIgnore
    protected org.locationtech.jts.geom.Geometry geom;

    /** Whether the record is a lot (checked) or an individual object (unchecked): {@code objet-lot}. */
    @Column(name = "object_or_lot")
    protected Boolean objectOrLot;

    /** Samples: what the sample is made of ({@code nature_prelevement}). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_sampling_nature")
    protected Concept samplingNature;

    /** Where the find is kept when it is not physically handed over to the State. */
    @Column(name = "storage_location", length = 1000)
    protected String storageLocation;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Specimen that = (Specimen) o;
        return Objects.equals(fullIdentifier, that.fullIdentifier);  // Compare based on RecordingUnit
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullIdentifier);  // Hash based on RecordingUnit
    }

    // utils
    public String displayFullIdentifier() {
        if(getFullIdentifier() == null) {
            if(getRecordingUnit().getFullIdentifier() == null) {
                throw new NullActionUnitIdentifierException("Recording identifier must be set");
            }
            return getRecordingUnit().getFullIdentifier() + "-" + (getIdentifier() == null ? "?" : getIdentifier());
        }
        else {
            return getFullIdentifier();
        }
    }




}