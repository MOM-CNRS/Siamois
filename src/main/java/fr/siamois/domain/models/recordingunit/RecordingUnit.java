package fr.siamois.domain.models.recordingunit;


import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.ReferencableEntity;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.exceptions.institution.NullInstitutionIdentifier;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;
import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

@Data
@Entity
@Table(name = "recording_unit")
@NoArgsConstructor
@Audited
public class RecordingUnit extends RecordingUnitParent implements ArkEntity, ReferencableEntity {

    public RecordingUnit(RecordingUnit recordingUnit) {
        setType(recordingUnit.getType());
        setActionUnit(recordingUnit.getActionUnit());
        setSecondaryType(recordingUnit.getSecondaryType());
        setSize(recordingUnit.getSize());
        setAltitude(recordingUnit.getAltitude());
        setCreatedByInstitution(recordingUnit.getCreatedByInstitution());
        setCreatedBy(recordingUnit.getCreatedBy());
        setAuthor(recordingUnit.getAuthor());
        setNormalizedInterpretation(recordingUnit.getNormalizedInterpretation());
        setGeomorphologicalCycle(recordingUnit.getGeomorphologicalCycle());
        setSpatialUnit(recordingUnit.getSpatialUnit());
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recording_unit_id", nullable = false)
    private Long id;

    @OneToMany(mappedBy = "unit1", fetch = FetchType.LAZY)
    private transient Set<StratigraphicRelationship> relationshipsAsUnit1 = new HashSet<>();

    @OneToMany(mappedBy = "unit2", fetch = FetchType.LAZY)
    private transient Set<StratigraphicRelationship> relationshipsAsUnit2 = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "recording_unit_hierarchy",
            joinColumns = {@JoinColumn(name = "fk_parent_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_child_id")}
    )
    private Set<RecordingUnit> children = new HashSet<>();

    @ManyToMany(mappedBy = "children")
    private Set<RecordingUnit> parents = new HashSet<>();


    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "recording_unit_contributors",
            joinColumns = @JoinColumn(name = "fk_recording_unit_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_person_id"))
    @NotAudited
    private List<Person> contributors = new ArrayList<>();

    @OneToMany(mappedBy = "recordingUnit")
    private Set<Specimen> specimenList;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "recording_unit_document",
            joinColumns = {@JoinColumn(name = "fk_recording_unit_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_document_id")}
    )
    private Set<Document> documents = new HashSet<>();

    @FieldCode
    public static final String TYPE_FIELD_CODE = "SIARU.TYPE";

    @FieldCode
    public static final String GEOMORPHO_CYCLE_FIELD_CODE = "SIARU.GEOMORPHO";

    @FieldCode
    public static final String INTERPRETATION_FIELD_CODE = "SIARU.INTERPRETATION";

    @FieldCode
    public static final String METHOD_FIELD_CODE = "SIARU.TYPE";

    @FieldCode
    public static final String STRATI_FIELD_CODE = "SIARU.STRATI";

    // Setters/Removers
    @Override
    public void setFormResponse(CustomFormResponse formResponse) {
        if (formResponse != null) {
            formResponse.setRecordingUnit(this);
        }
        this.formResponse = formResponse;
    }

    // utils
    public String displayFullIdentifier() {
        if (getFullIdentifier() == null) {
            if (getCreatedByInstitution().getIdentifier() == null) {
                throw new NullInstitutionIdentifier("Institution identifier must be set");
            }
            if (getActionUnit().getIdentifier() == null) {
                throw new NullActionUnitIdentifierException("Action identifier must be set");
            }
            return getCreatedByInstitution().getIdentifier() + "-" + getActionUnit().getIdentifier() + "-" + (getIdentifier() == null ? "?" : getIdentifier());
        } else {
            return getFullIdentifier();
        }
    }


    @Override
    public String getTableName() {
        return "RECORDING_UNIT";
    }

    @Override
    public String toString() {
        return String.format("Recording Unit %s", displayFullIdentifier());
    }

    @Transient
    @JsonIgnore
    public List<String> getBindableFieldNames() {
        return List.of("creationTime", "startDate", "endDate", "identifier",
                "contributors", "type", "secondaryType", "thirdType", "actionUnit", "spatialUnit",
                "geomorphologicalCycle", "normalizedInterpretation", "author");
    }

    // ----------- Concepts for system fields
    // Authors
    @Transient
    @JsonIgnore
    private static Concept authorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286194")
            .build();


    // Excavators
    @Transient
    @JsonIgnore
    private static Concept excavatorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286195")
            .build();

    // Recording Unit type
    @Transient
    @JsonIgnore
    private static Concept recordingUnitTypeConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287605")
            .build();


    // Date
    @Transient
    @JsonIgnore
    private static Concept openingDateConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286198")
            .build();


    // Spatial Unit
    @Transient
    @JsonIgnore
    private static Concept spatialUnitConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286245")
            .build();

    // Action unit
    @Transient
    @JsonIgnore
    private static Concept actionUnitConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286244")
            .build();

    @Transient
    @JsonIgnore
    private static CustomFieldSelectOnePerson authorField = new CustomFieldSelectOnePerson.Builder()
            .label("recordingunit.field.mainAuthor")
            .isSystemField(true)
            .valueBinding("author")
            .concept(authorsConcept)
            .build();

    @Transient
    @JsonIgnore
    private static CustomFieldSelectMultiplePerson excavatorsField = new CustomFieldSelectMultiplePerson.Builder()
            .label("recordingunit.field.excavators")
            .isSystemField(true)
            .valueBinding("excavators")
            .concept(excavatorsConcept)
            .build();
    public static final String MR_2_RECORDING_UNIT_TYPE_CHIP = "mr-2 recording-unit-type-chip";
    public static final String BI_BI_PENCIL_SQUARE = "bi bi-pencil-square";
    @Transient
    @JsonIgnore
    private static CustomFieldSelectOneFromFieldCode recordingUnitTypeField = new CustomFieldSelectOneFromFieldCode.Builder()
            .label("recordingunit.property.type")
            .isSystemField(true)
            .valueBinding("type")
            .styleClass(MR_2_RECORDING_UNIT_TYPE_CHIP)
            .iconClass(BI_BI_PENCIL_SQUARE)
            .fieldCode(RecordingUnit.TYPE_FIELD_CODE)
            .concept(recordingUnitTypeConcept)
            .build();


    @Transient
    @JsonIgnore
    private static CustomFieldDateTime openingDateField = new CustomFieldDateTime.Builder()
            .label("recordingunit.field.openingDate")
            .isSystemField(true)
            .valueBinding("startDate")
            .showTime(false)
            .concept(openingDateConcept)
            .build();

    @Transient
    @JsonIgnore
    private static CustomFieldSelectOneSpatialUnit spatialUnitField = new CustomFieldSelectOneSpatialUnit.Builder()
            .label("recordingunit.field.spatialUnit")
            .isSystemField(true)
            .valueBinding("spatialUnit")
            .concept(spatialUnitConcept)
            .build();

    @Transient
    @JsonIgnore
    private static CustomFieldSelectOneActionUnit actionUnitField = new CustomFieldSelectOneActionUnit.Builder()
            .label("recordingunit.field.actionUnit")
            .isSystemField(true)
            .valueBinding("actionUnit")
            .concept(actionUnitConcept)
            .build();

    // ----------- Concepts for system fields
    // Recording unit identifier
    @Transient
    @JsonIgnore
    private static Concept recordingUnitIdConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286193")
            .build();

    @Transient
    @JsonIgnore
    private static Concept recordingUnitSecondaryTypeConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286196")
            .build();
    @Transient
    @JsonIgnore
    private static Concept recordingUnitIdentificationConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286197")
            .build();

    // Date
    @Transient
    @JsonIgnore
    private static Concept creationDateConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286200")
            .build();

    @Transient
    @JsonIgnore
    private static Concept closingDateConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286199")
            .build();

    // Action Unit

    // Fields
    @Transient
    @JsonIgnore
    private static CustomFieldText recordingUnitIdField = new CustomFieldText.Builder()
            .label("recordingunit.field.identifier")
            .isSystemField(true)
            .valueBinding("fullIdentifier")
            .concept(recordingUnitIdConcept)
            .build();


    @Transient
    @JsonIgnore
    private static CustomFieldSelectOneConceptFromChildrenOfConcept recordingUnitSecondaryTypeField = new CustomFieldSelectOneConceptFromChildrenOfConcept.Builder()
            .label("recordingunit.field.secondaryType")
            .isSystemField(true)
            .valueBinding("secondaryType")
            .styleClass(MR_2_RECORDING_UNIT_TYPE_CHIP)
            .iconClass(BI_BI_PENCIL_SQUARE)
            .parentField(recordingUnitTypeField)
            .concept(recordingUnitSecondaryTypeConcept)
            .build();

    @Transient
    @JsonIgnore
    private static CustomFieldSelectOneConceptFromChildrenOfConcept recordingUnitIdentificationField = new CustomFieldSelectOneConceptFromChildrenOfConcept.Builder()
            .label("recordingunit.field.thirdType")
            .isSystemField(true)
            .valueBinding("thirdType")
            .styleClass(MR_2_RECORDING_UNIT_TYPE_CHIP)
            .iconClass(BI_BI_PENCIL_SQUARE)
            .parentField(recordingUnitSecondaryTypeField)
            .concept(recordingUnitIdentificationConcept)
            .build();

    @Transient
    @JsonIgnore
    private static CustomFieldDateTime creationDateField = new CustomFieldDateTime.Builder()
            .label("recordingunit.field.creationDate")
            .isSystemField(true)
            .showTime(true)
            .valueBinding("creationTime")
            .concept(creationDateConcept)
            .build();


    @Transient
    @JsonIgnore
    private static CustomFieldDateTime closingDateField = new CustomFieldDateTime.Builder()
            .label("recordingunit.field.closingDate")
            .isSystemField(true)
            .valueBinding("endDate")
            .showTime(false)
            .concept(closingDateConcept)
            .build();


    public static final String COMMON_HEADER_GENERAL = "common.header.general";
    @Transient
    @JsonIgnore
    public static final CustomForm NEW_UNIT_FORM = new CustomForm.Builder()
            .name("Details tab form")
            .description("Contains the main form")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name(COMMON_HEADER_GENERAL)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .isRequired(true)
                                                    .field(actionUnitField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .isRequired(true)
                                                    .field(spatialUnitField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .isRequired(true)
                                                    .field(authorField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .isRequired(true)
                                                    .field(recordingUnitTypeField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .isRequired(true)
                                                    .field(openingDateField)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();

    // Details form
    @Transient
    @JsonIgnore
    public static final CustomForm DETAILS_FORM = new CustomForm.Builder()
            .name("Details tab form")
            .description("Contains the main form")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name(COMMON_HEADER_GENERAL)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitIdField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(actionUnitField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(spatialUnitField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(authorField)
                                                    .build())
                                            .build()
                            ).addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitTypeField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitSecondaryTypeField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitIdentificationField)
                                                    .build())
                                            .build()
                            ).addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(creationDateField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(openingDateField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(closingDateField)
                                                    .build())
                                            .build()
                            )
                            .build()
            )
            .build();

    // Details form
    @Transient
    @JsonIgnore
    public static final CustomForm OVERVIEW_FORM = new CustomForm.Builder()
            .name("Overview tab form")
            .description("Contains the overview")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name(COMMON_HEADER_GENERAL)
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(recordingUnitTypeField)
                                                    .build())
                                            .build()
                            )
                            .build()
            )
            .build();

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}