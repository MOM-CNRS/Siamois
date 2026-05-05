package fr.siamois.domain.models.specimen.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Transient;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

public abstract class SpecimenForm {

    protected SpecimenForm() {}

    // ----------- Concepts for system fields

    // Specimen identifier
    protected static Concept specimenIdConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286250")
            .build();

    // Specimen identifier
    protected static Concept specimenOtherIdConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4282391")
            .build();

    // Authors
    @Transient
    @JsonIgnore
    protected static Concept authorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286246")
            .build();

    // Excavators
    @Transient
    @JsonIgnore
    protected static Concept collectorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286247")
            .build();

    // Specimen type
    @Transient
    @JsonIgnore
    protected static Concept specimenTypeConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4282392")
            .build();

    // Specimen category
    @Transient
    @JsonIgnore
    protected static Concept specimenCategoryConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286248")
            .build();

    // Date
    @Transient
    @JsonIgnore
    protected static Concept collectionDateConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286249")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept recordingUnitConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290139")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept isPartOfConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290140")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept containsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290141")
            .build();

    // --------------- Fields

    // Fields
    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneRecordingUnit recordingUnitField = CustomFieldSelectOneRecordingUnit.builder()
            .label("specimen.field.recordingUnit")
            .isSystemField(true)
            .id(1L)
            .valueBinding("recordingUnit")
            .concept(recordingUnitConcept)
            .build();
    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultipleSpecimen isPartOfField = CustomFieldSelectMultipleSpecimen.builder()
            .label("specimen.field.isPartOf")
            .isSystemField(true)
            .id(2L)
            .valueBinding("parents")
            .concept(isPartOfConcept)
            .build();
    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultipleSpecimen containsField = CustomFieldSelectMultipleSpecimen.builder()
            .label("specimen.field.contains")
            .isSystemField(true)
            .id(3L)
            .valueBinding("children")
            .concept(containsConcept)
            .build();

    // Fields
    @Transient
    @JsonIgnore
    protected static CustomFieldText specimenIdField = CustomFieldText.builder()
            .label("recordingunit.field.identifier")
            .isSystemField(true)
            .id(4L)
            .valueBinding("fullIdentifier")
            .concept(specimenIdConcept)
            .build();
    @Transient
    @JsonIgnore
    protected static CustomFieldText specimenOtherIdField = CustomFieldText.builder()
            .label("recordingunit.field.otherIdentifier")
            .isSystemField(true)
            .id(5L)
            .valueBinding("otherIdentifier")
            .concept(specimenOtherIdConcept)
            .build();


    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultiplePerson authorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.authors")
            .isSystemField(true)
            .id(2L)
            .valueBinding("authors")
            .concept(authorsConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultiplePerson collectorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.collectors")
            .isSystemField(true)
            .id(3L)
            .valueBinding("collectors")
            .concept(collectorsConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode specimenTypeField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.type")
            .isSystemField(true)
            .valueBinding("type")
            .id(4L)
            .styleClass("mr-2 specimen-type-chip")
            .iconClass("bi bi-bucket")
            .fieldCode(Specimen.CATEGORY_FIELD)
            .concept(specimenTypeConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode specimenCategoryField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.type")
            .isSystemField(true)
            .valueBinding("type")
            .id(5L)
            .styleClass("mr-2 specimen-type-chip")
            .iconClass("bi bi-bucket")
            .fieldCode(Specimen.CAT_FIELD)
            .concept(specimenCategoryConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldDateTime collectionDateField =  CustomFieldDateTime.builder()
            .label("specimen.field.collectionDate")
            .isSystemField(true)
            .id(6L)
            .valueBinding("collectionDate")
            .showTime(false)
            .concept(collectionDateConcept)
            .build();

    

}
