package fr.siamois.domain.models.actionunit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.institution.NullInstitutionIdentifier;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;
import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

@Data
@Entity
@Table(name = "action_unit", uniqueConstraints = @UniqueConstraint(columnNames = "identifier"))
@Audited
public class ActionUnit extends ActionUnitParent implements ArkEntity {

    private static final String SPATIAL_UNIT_CONTEXT_LABEL_CODE = "common.label.spatialContext";
    private static final String GENERAL_LABEL_CODE = "common.header.general";
    private static final String DETAIL_TAB_NAME = "\"Details tab form\"";

    public ActionUnit() {
    }

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

// ----------- Concepts for system fields


    @Transient
    @JsonIgnore
    public static final Concept ACTION_UNIT_TYPE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4282386")
            .build();

    // unit name
    @Transient
    @JsonIgnore
    public static final Concept NAME_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4285848")
            .build();

    // unit id
    @Transient
    @JsonIgnore
    public static final Concept IDENTIFIER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286368")
            .build();


    // spatial context
    @Transient
    @JsonIgnore
    public static final Concept SPATIAL_CONTEXT_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286503")
            .build();

    // begin date
    @Transient
    @JsonIgnore
    public static final Concept BEGIN_DATE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287545")
            .build();

    // end date
    @Transient
    @JsonIgnore
    public static final Concept END_DATE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287546")
            .build();

    // end date
    @Transient
    @JsonIgnore
    public static final Concept ACTION_CODE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287547")
            .build();


    // --------------- Fields
    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneFromFieldCode ACTION_UNIT_TYPE_FIELD = new CustomFieldSelectOneFromFieldCode.Builder()
            .label("specimen.field.category")
            .isSystemField(true)
            .valueBinding("type")
            .styleClass("mr-2 action-unit-type-chip")
            .iconClass("bi bi-box2")
            .fieldCode(ActionUnit.TYPE_FIELD_CODE)
            .concept(ACTION_UNIT_TYPE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText NAME_FIELD = new CustomFieldText.Builder()
            .label("common.label.name")
            .isSystemField(true)
            .valueBinding("name")
            .concept(NAME_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText IDENTIFIER_FIELD = new CustomFieldText.Builder()
            .label("common.label.identifier")
            .isSystemField(true)
            .autoGenerationFunction(AbstractSingleEntity::generateRandomActionUnitIdentifier)
            .valueBinding("identifier")
            .concept(IDENTIFIER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldSelectMultipleSpatialUnitTree SPATIAL_CONTEXT_FIELD = new CustomFieldSelectMultipleSpatialUnitTree.Builder()
            .label(SPATIAL_UNIT_CONTEXT_LABEL_CODE)
            .isSystemField(true)
            .valueBinding("spatialContext")
            .concept(SPATIAL_CONTEXT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    private static final CustomFieldDateTime BEGIN_DATE_FIELD = new CustomFieldDateTime.Builder()
            .label("common.field.beginDate")
            .isSystemField(true)
            .valueBinding("beginDate")
            .showTime(false)
            .concept(BEGIN_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    private static final CustomFieldDateTime END_DATE_FIELD = new CustomFieldDateTime.Builder()
            .label("common.field.endDate")
            .isSystemField(true)
            .valueBinding("endDate")
            .showTime(false)
            .concept(END_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    private static final CustomFieldSelectOneActionCode ACTION_CODE_FIELD = new CustomFieldSelectOneActionCode.Builder()
            .label("actionunit.field.actionCode")
            .isSystemField(true)
            .valueBinding("primaryActionCode")
            .concept(ACTION_CODE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomForm NEW_UNIT_FORM = new CustomForm.Builder()
            .name(DETAIL_TAB_NAME)
            .description("")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name(GENERAL_LABEL_CODE)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(IDENTIFIER_FIELD)
                                                    .isRequired(true)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(NAME_FIELD)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(ACTION_UNIT_TYPE_FIELD)
                                                    .build())
                                            .build()
                            ).build()
            )
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name(SPATIAL_UNIT_CONTEXT_LABEL_CODE)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className("ui-g-12")
                                                    .field(SPATIAL_CONTEXT_FIELD)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();

    @Transient
    @JsonIgnore
    public static final CustomForm OVERVIEW_FORM = new CustomForm.Builder()
            .name(DETAIL_TAB_NAME)
            .description("")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name(GENERAL_LABEL_CODE)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(ACTION_UNIT_TYPE_FIELD)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();

    @Transient
    @JsonIgnore
    public static final CustomForm DETAILS_FORM = new CustomForm.Builder()
            .name(DETAIL_TAB_NAME)
            .description("")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name(GENERAL_LABEL_CODE)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(NAME_FIELD)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(IDENTIFIER_FIELD)
                                                    .isRequired(true)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(ACTION_UNIT_TYPE_FIELD)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(ACTION_CODE_FIELD)
                                                    .build())
                                            .build()
                            )
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(BEGIN_DATE_FIELD)
                                                    .isRequired(false)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(END_DATE_FIELD)
                                                    .isRequired(false)
                                                    .build())
                                            .build()
                            ).build()
            )
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name(SPATIAL_UNIT_CONTEXT_LABEL_CODE)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className("ui-g-12 ui-md-12 ui-lg-12")
                                                    .field(SPATIAL_CONTEXT_FIELD)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();

}