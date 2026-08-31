package fr.siamois.domain.models.actionunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.form.ActionUnitDetailsForm;
import fr.siamois.domain.models.actionunit.form.ActionUnitNewForm;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.institution.NullInstitutionIdentifier;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.form.dto.FormUiDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Entity
@Table(name = "action_unit",
        uniqueConstraints = @UniqueConstraint(columnNames = {"identifier", "fk_institution_id"}),
        indexes = {
                @Index(columnList = "name", name = "idx_action_unit_name"),
                @Index(columnList = "full_identifier", name = "idx_action_unit_full_identifier"),
                @Index(columnList = "fk_institution_id", name = "idx_action_unit_institution")
        }
)
@Audited
public class ActionUnit extends TraceableEntity implements ArkEntity {

    public ActionUnit() {
    }

    @SuppressWarnings("CopyConstructorMissesField")
    public ActionUnit(@NonNull ActionUnit unit) {
        this.setName(unit.getName());

        this.setType(unit.getType());
        this.setCreatedByInstitution(unit.getCreatedByInstitution());
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_unit_id", nullable = false)
    private Long id;

    @OneToMany
    @JoinTable(
            name = "action_unit_document",
            joinColumns = {@JoinColumn(name = "fk_action_unit_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_document_id")}
    )
    private Set<Document> documents = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "action_action_code",
            joinColumns = {@JoinColumn(name = "fk_action_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_action_code_id")}
    )
    private Set<ActionCode> secondaryActionCodes = new HashSet<>();


    @ManyToMany
    @JoinTable(
            name = "action_hierarchy",
            joinColumns = {@JoinColumn(name = "fk_parent_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_child_id")}
    )
    private Set<ActionUnit> children = new HashSet<>();

    @ManyToMany(mappedBy = "children")
    private Set<ActionUnit> parents = new HashSet<>();

    @OneToMany(mappedBy = "actionUnit")
    private Set<RecordingUnit> recordingUnitList;

    @ManyToOne
    @JoinColumn(name = "fk_main_location")
    private SpatialUnit mainLocation;

    @ManyToMany
    @JoinTable(
            name = "action_unit_spatial_context",
            joinColumns = {@JoinColumn(name = "fk_action_unit_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_spatial_unit_id")}
    )
    private Set<SpatialUnit> spatialContext = new HashSet<>();

    @FieldCode
    public static final String TYPE_FIELD_CODE = "SIAAU.TYPE";

    @FieldCode
    public static final String STATUS_FIELD_CODE = "SIAAU.STATUS";

    @FieldCode
    public static final String FIELD_STATUS_FIELD_CODE = "SIAAU.FIELD_STATUS";

    @FieldCode
    public static final String SYSTEM_FIELD_CODE = "SIAAU.SYSTEM";

    @FieldCode
    public static final String DEVELOPMENT_NATURE_FIELD_CODE = "SIAAU.DEVELOPMENT_NATURE";

    @FieldCode
    public static final String PERIODS_FIELD_CODE = "SIAAU.PERIODS";

    @FieldCode
    public static final String SUBJECTS_FIELD_CODE = "SIAAU.SUBJECTS";

    public String displayFullIdentifier() {
        if (getFullIdentifier() == null) {
            if (getCreatedByInstitution().getIdentifier() == null) {
                throw new NullInstitutionIdentifier("Institution identifier must be set");
            }
            return getCreatedByInstitution().getIdentifier() + "-" + (getIdentifier() == null ? '?' : getIdentifier());
        } else {
            return getFullIdentifier();
        }
    }

    @Column(name = "begin_date")
    protected OffsetDateTime beginDate;

    @Column(name = "end_date")
    protected OffsetDateTime endDate;

    @NotNull
    @Column(name = "name", nullable = false)
    protected String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_type")
    protected Concept type;

    @OneToOne(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;


    @ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE})
    @JoinColumn(name = "fk_primary_action_code")
    protected ActionCode primaryActionCode;

    @NotNull
    @Column(name = "identifier")
    protected String identifier;

    @NotNull
    @Column(name = "full_identifier")
    protected String fullIdentifier;


    /**
     * This field is set to true when the action unit has children in the institution.
     * The variable change is triggered when a new row is inserted in action_hierarchy
     * and when this action_unit's id is the parent.
     * The trigger trg_after_insert_au_hierarchy executes mark_au_as_not_leaf
     */
    @Column(name = "has_childrens", columnDefinition = "boolean default false")
    protected boolean hasChildrens = false;

    // --------------- Documentation / administrative fields (SIAAU.*)

    @Column(name = "oa_code")
    protected String oaCode;

    @Column(name = "prescription_order_number")
    protected String prescriptionOrderNumber;

    @Column(name = "prescription_order_date")
    protected OffsetDateTime prescriptionOrderDate;

    @Column(name = "scientific_manager")
    protected String scientificManager;

    @Column(name = "host_structure")
    protected String hostStructure;

    @Column(name = "developer")
    protected String developer;

    @Column(name = "scientific_notice", length = 5000)
    protected String scientificNotice;

    @Column(name = "comments", length = 5000)
    protected String comments;

    @Column(name = "zmin")
    protected Double zmin;

    @Column(name = "zmax")
    protected Double zmax;

    @Column(name = "designation_order_number")
    protected String designationOrderNumber;

    @Column(name = "designation_order_date")
    protected OffsetDateTime designationOrderDate;

    @Column(name = "prescribed_area")
    protected Double prescribedArea;

    @Column(name = "excavated_area")
    protected Double excavatedArea;

    @Column(name = "accessible_area")
    protected Double accessibleArea;

    @Column(name = "opening_rate")
    protected Double openingRate;

    @Column(name = "volume_count")
    protected Integer volumeCount;

    @Column(name = "page_count")
    protected Integer pageCount;

    @Column(name = "figure_count")
    protected Integer figureCount;

    @Column(name = "appendix_count")
    protected Integer appendixCount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_status")
    protected Concept status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_field_status")
    protected Concept fieldStatus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_system")
    protected Concept system;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_development_nature")
    protected Concept developmentNature;

    @ManyToMany
    @JoinTable(
            name = "action_unit_period",
            joinColumns = {@JoinColumn(name = "fk_action_unit_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_concept_id")}
    )
    protected Set<Concept> periods = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "action_unit_subject",
            joinColumns = {@JoinColumn(name = "fk_action_unit_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_concept_id")}
    )
    protected Set<Concept> subjects = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionUnit that = (ActionUnit) o;
        return Objects.equals(fullIdentifier, that.fullIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullIdentifier);
    }

    @Override
    public String toString() {
        return String.format("Action Unit %s", displayFullIdentifier());
    }


    @Transient
    @JsonIgnore
    public static final FormUiDto NEW_UNIT_FORM = ActionUnitNewForm.build();


    @Transient
    @JsonIgnore
    public static final FormUiDto DETAILS_FORM = ActionUnitDetailsForm.build();

    public String getSpatialContextNames() {
        if (spatialContext == null || spatialContext.isEmpty()) {
            return "Aucun contexte spatial";
        }
        return spatialContext.stream()
                .map(SpatialUnit::getName)
                .collect(Collectors.joining(", "));
    }


}
