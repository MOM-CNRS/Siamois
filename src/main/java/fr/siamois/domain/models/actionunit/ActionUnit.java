package fr.siamois.domain.models.actionunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.actionunit.form.ActionNewUnitForm;
import fr.siamois.domain.models.actionunit.form.ActionUnitDetailsForm;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.institution.NullInstitutionIdentifier;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Entity
@Table(name = "action_unit", uniqueConstraints = @UniqueConstraint(columnNames = "identifier"))
@Audited
public class ActionUnit extends ActionUnitParent implements ArkEntity {

    public ActionUnit() {}

    public ActionUnit(ActionUnit unit) {
        this.setName(unit.getName());
        this.setValidated(false);
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

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Action Unit %s", displayFullIdentifier());
    }

    @Transient
    @JsonIgnore
    public List<String> getBindableFieldNames() {
        return List.of("type", "name", "identifier", "spatialContext", "beginDate", "endDate", "primaryActionCode");
    }

    @Transient
    @JsonIgnore
    public static final CustomForm NEW_UNIT_FORM = ActionNewUnitForm.build();


    @Transient
    @JsonIgnore
    public static final CustomForm DETAILS_FORM = ActionUnitDetailsForm.build();

}