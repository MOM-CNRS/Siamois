package fr.siamois.domain.models.specimen.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
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

    @Transient
    @JsonIgnore
    protected static Concept isolationNumberConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4282391")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept containerConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290154")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept materialConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290157")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept descriptionConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290158")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept commentsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290159")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept materialClassConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290155")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept normalizedInterpretationConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290156")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept taqConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287614")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept tpqConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287613")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept chronologicalAttributionConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290160")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept numberOfElementConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290161")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept weightConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290162")
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
    protected static CustomFieldText isolationNumberField = CustomFieldText.builder()
            .label("recordingunit.field.isolationIdentifier")
            .isSystemField(true)
            .id(10L)
            .valueBinding("isolationNumber")
            .concept(isolationNumberConcept)
            .build();


    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultiplePerson authorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.authors")
            .isSystemField(true)
            .id(6L)
            .valueBinding("authors")
            .concept(authorsConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultiplePerson collectorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.collectors")
            .isSystemField(true)
            .id(7L)
            .valueBinding("collectors")
            .concept(collectorsConcept)
            .build();


    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode specimenCategoryField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.category")
            .isSystemField(true)
            .valueBinding("category")
            .id(9L)
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
            .id(11L)
            .valueBinding("collectionDate")
            .showTime(false)
            .concept(collectionDateConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode materialField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.material")
            .isSystemField(true)
            .id(13L)
            .valueBinding("material")
            .concept(materialConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode materialClassField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.materialClass")
            .isSystemField(true)
            .id(14L)
            .valueBinding("materialClass")
            .concept(materialClassConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode normalizedInterpretationField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.normalizedInterpretation")
            .isSystemField(true)
            .id(15L)
            .valueBinding("normalizedInterpretation")
            .concept(normalizedInterpretationConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldText descriptionField = CustomFieldText.builder()
            .label("recordingunit.field.description")
            .isSystemField(true)
            .id(16L)
            .valueBinding("description")
            .concept(descriptionConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldText commentsField = CustomFieldText.builder()
            .label("recordingunit.field.comments")
            .isSystemField(true)
            .id(17L)
            .valueBinding("comments")
            .concept(commentsConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode chronologicalAttributionField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.chronologicalAttribution")
            .isSystemField(true)
            .id(18L)
            .valueBinding("chronologicalAttribution")
            .concept(chronologicalAttributionConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldInteger taqField =  CustomFieldInteger.builder()
            .label("specimen.field.taq")
            .isSystemField(true)
            .id(19L)
            .valueBinding("taq")
            .concept(taqConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldInteger tpqField =  CustomFieldInteger.builder()
            .label("specimen.field.tpq")
            .isSystemField(true)
            .id(20L)
            .valueBinding("tpq")
            .concept(tpqConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldInteger numberOfElementField =  CustomFieldInteger.builder()
            .label("specimen.field.numberOfElement")
            .isSystemField(true)
            .id(21L)
            .valueBinding("numberOfElements")
            .concept(numberOfElementConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldMeasurement weightField =  CustomFieldMeasurement.builder()
            .label("specimen.field.weight")
            .isSystemField(true)
            .id(22L)
            .unit(new UnitDefinition(
                    null,
                    null,
                    "Kilo",
                    "kg",
                    UnitDefinition.Dimension.MASS,
                    1000.0,
                    false
            ))
            .valueBinding("weight")
            .concept(weightConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultipleContainer containerField =  CustomFieldSelectMultipleContainer.builder()
            .label("specimen.field.containers")
            .isSystemField(true)
            .id(23L)
            .valueBinding("containers")
            .concept(containsConcept)
            .build();



}
