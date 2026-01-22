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
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
@Table(name = "action_unit", uniqueConstraints = @UniqueConstraint(columnNames = "identifier"))
@Audited
public class ActionUnit extends TraceableEntity implements ArkEntity {

    public ActionUnit() {
        this.maxRecordingUnitCode = Integer.MAX_VALUE;
        this.minRecordingUnitCode = 1;
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

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "pk.actionUnit")
    private Set<ActionUnitFormMapping> formsAvailable = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
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

    @ManyToMany
    @JoinTable(
            name = "action_unit_spatial_context",
            joinColumns = {@JoinColumn(name = "fk_action_unit_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_spatial_unit_id")}
    )
    private Set<SpatialUnit> spatialContext = new HashSet<>();

    @Nullable
    @Column(name = "recording_unit_identifier_format")
    private String recordingUnitIdentifierFormat = "{NUM_UE}";

    @Nullable
    @Column(name = "recording_unit_identifier_lang")
    private String recordingUnitIdentifierLang;

    @FieldCode
    public static final String TYPE_FIELD_CODE = "SIAAU.TYPE";

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
    @Column(name="identifier")
    protected String identifier;

    @NotNull
    @Column(name="full_identifier")
    protected String fullIdentifier;

    @NotNull
    @Column(name="max_recording_unit_code", nullable = false)
    protected Integer maxRecordingUnitCode;

    @NotNull
    @Column(name="min_recording_unit_code")
    protected Integer minRecordingUnitCode;

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
    @SuppressWarnings("unused")
    public List<String> getBindableFieldNames() {
        return List.of("type", "name", "identifier", "spatialContext", "beginDate", "endDate", "primaryActionCode");
    }

    @JsonIgnore
    public ActionUnitResolveConfig resolveConfig() {
        if (recordingUnitIdentifierFormat == null || recordingUnitIdentifierFormat.isEmpty())
            return ActionUnitResolveConfig.NONE;

        final boolean containsRuNumber = recordingUnitIdentifierFormat.contains("NUM_UE");
        final boolean containsRuType = recordingUnitIdentifierFormat.contains("TYPE_UE");
        final boolean containsParentNumber = recordingUnitIdentifierFormat.contains("NUM_PARENT");

        if (containsRuNumber && containsRuType && containsParentNumber) {
            return ActionUnitResolveConfig.PARENT_TYPE;
        } else if (containsRuNumber && containsRuType) {
            return ActionUnitResolveConfig.TYPE_UNIQUE;
        } else if (containsRuNumber && containsParentNumber) {
            return ActionUnitResolveConfig.PARENT;
        } else if (containsRuNumber) {
            return ActionUnitResolveConfig.UNIQUE;
        } else {
            return ActionUnitResolveConfig.NONE;
        }
    }

    @Transient
    @JsonIgnore
    public static final CustomForm NEW_UNIT_FORM = ActionUnitNewForm.build();


    @Transient
    @JsonIgnore
    public static final CustomForm DETAILS_FORM = ActionUnitDetailsForm.build();

}