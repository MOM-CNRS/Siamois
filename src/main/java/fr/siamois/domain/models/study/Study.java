package fr.siamois.domain.models.study;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A study: a piece of work done on recording units and/or finds, attached to exactly one project.
 * <p>
 * The {@link #type} selects the form configuration. By default there are three types: the study of
 * finds and the study of recording units (which both bear the linked recording units and finds), and
 * the default treatment, whose fields are the columns of the "traitement" sheet of the national
 * reference of the SDA.
 * <p>
 * The links to recording units and finds are owned by this side (one join table per target type).
 */
@Data
@Entity
@Table(name = "study",
        indexes = @Index(name = "idx_study_action_unit", columnList = "fk_action_unit_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_study_project_identifier",
                columnNames = {"fk_action_unit_id", "identifier"}))
@Audited
@NoArgsConstructor
public class Study extends TraceableEntity {

    @FieldCode
    public static final String TYPE_FIELD = "SIAST.TYPE";

    @FieldCode
    public static final String METHOD_FIELD_CODE = "SIAST.METHODE";

    /** The kind of treatment ({@code type_traitement}); the vocabulary is suggested and can be extended. */
    @FieldCode
    public static final String TREATMENT_TYPE_FIELD_CODE = "SIAST.TYPETRAIT";

    public static final int MAX_TEXT_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "study_id", nullable = false)
    private Long id;

    // ---------------------------------------------------------------- Identification

    /** Unique within the project; generated from the project's identifier format, then editable. */
    @NotNull
    @Column(name = "identifier", nullable = false)
    protected String identifier;

    /** Raw allocated counter value; kept separately because it cannot be inferred from the format. */
    @Column(name = "generated_number")
    protected Integer generatedNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_action_unit_id")
    protected ActionUnit actionUnit;

    /** Selects the study's form configuration. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_type")
    protected Concept type;

    // ---------------------------------------------------------------- Study

    @Column(name = "study_date")
    protected OffsetDateTime studyDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_method")
    protected Concept method;

    // ---------------------------------------------------------------- Treatment (SDA "traitement" sheet)

    /** {@code type_traitement}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_treatment_type")
    protected Concept treatmentType;

    /** {@code code_rapport}: inventory code of the related report in the documentation sheet. */
    @Column(name = "report_code")
    protected String reportCode;

    /** Start of {@code periode_traitement}. */
    @Column(name = "treatment_start")
    protected OffsetDateTime treatmentStart;

    /** End of {@code periode_traitement}; empty when the treatment lasts a single day. */
    @Column(name = "treatment_end")
    protected OffsetDateTime treatmentEnd;

    /** {@code lieu_traitement}. */
    @Column(name = "treatment_place")
    protected String treatmentPlace;

    /** {@code nom_responsable_traitement}, as "Nom & Prénom". */
    @Column(name = "treatment_manager")
    protected String treatmentManager;

    /** {@code identifiant_lie_au_traitement}. */
    @Column(name = "treatment_identifier")
    protected String treatmentIdentifier;

    /** {@code informations_complementaires_traitement}. */
    @Column(name = "treatment_comments", length = MAX_TEXT_LENGTH)
    protected String treatmentComments;

    // ---------------------------------------------------------------- Links to other entities

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "study_recording_unit",
            joinColumns = @JoinColumn(name = "fk_study_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_recording_unit_id")
    )
    @NotAudited
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<RecordingUnit> recordingUnits = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "study_specimen",
            joinColumns = @JoinColumn(name = "fk_study_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_specimen_id")
    )
    @NotAudited
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Specimen> specimens = new HashSet<>();

}
