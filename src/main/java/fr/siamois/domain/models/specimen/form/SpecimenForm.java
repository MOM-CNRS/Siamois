package fr.siamois.domain.models.specimen.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.container.CustomFieldSelectMultipleContainer;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.phase.CustomFieldSelectMultiplePhase;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectOneRecordingUnit;
import fr.siamois.domain.models.form.customfield.specimen.CustomFieldSelectMultipleSpecimen;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
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
            .externalId("4290142")
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
            .id(-401L)
            .valueBinding("recordingUnit")
            .concept(recordingUnitConcept)
            .build();
    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultipleSpecimen isPartOfField = CustomFieldSelectMultipleSpecimen.builder()
            .label("specimen.field.isPartOf")
            .isSystemField(true)
            .id(-402L)
            .valueBinding("parents")
            .concept(isPartOfConcept)
            .build();
    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultipleSpecimen containsField = CustomFieldSelectMultipleSpecimen.builder()
            .label("specimen.field.contains")
            .isSystemField(true)
            .id(-403L)
            .valueBinding("children")
            .concept(containsConcept)
            .build();

    // Fields
    @Transient
    @JsonIgnore
    protected static CustomFieldText specimenIdField = CustomFieldText.builder()
            .label("recordingunit.field.identifier")
            .isSystemField(true)
            .id(-404L)
            .valueBinding("fullIdentifier")
            .concept(specimenIdConcept)
            .build();
    @Transient
    @JsonIgnore
    protected static CustomFieldText specimenOtherIdField = CustomFieldText.builder()
            .label("recordingunit.field.otherIdentifier")
            .isSystemField(true)
            .id(-405L)
            .valueBinding("otherIdentifier")
            .concept(specimenOtherIdConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldText isolationNumberField = CustomFieldText.builder()
            .label("recordingunit.field.isolationIdentifier")
            .isSystemField(true)
            .id(-406L)
            .valueBinding("isolationNumber")
            .concept(isolationNumberConcept)
            .build();


    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultiplePerson authorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.authors")
            .isSystemField(true)
            .id(-407L)
            .valueBinding("authors")
            .concept(authorsConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultiplePerson collectorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.collectors")
            .isSystemField(true)
            .id(-408L)
            .valueBinding("collectors")
            .concept(collectorsConcept)
            .build();


    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode specimenCategoryField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.category")
            .isSystemField(true)
            .valueBinding("category")
            .id(-409L)
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
            .id(-410L)
            .valueBinding("collectionDate")
            .showTime(false)
            .concept(collectionDateConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultipleFromFieldCode materialField =  CustomFieldSelectMultipleFromFieldCode.builder()
            .label("specimen.field.material")
            .isSystemField(true)
            .isSystemField(true)
            .id(-411L)
            .fieldCode(Specimen.MATIERE_FIELD)
            .valueBinding("material")
            .concept(materialConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultipleFromFieldCode materialClassField =  CustomFieldSelectMultipleFromFieldCode.builder()
            .label("specimen.field.materialClass")
            .isSystemField(true)
            .id(-412L)
            .fieldCode(Specimen.CLASS_FIELD)
            .valueBinding("materialClass")
            .concept(materialClassConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode normalizedInterpretationField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.normalizedInterpretation")
            .isSystemField(true)
            .id(-413L)
            .fieldCode(Specimen.INTERPRETATION_FIELD)
            .valueBinding("normalizedInterpretation")
            .concept(normalizedInterpretationConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldText descriptionField = CustomFieldText.builder()
            .label("recordingunit.field.description")
            .isSystemField(true)
            .id(-414L)
            .isTextArea(true)
            .valueBinding("description")
            .concept(descriptionConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldText commentsField = CustomFieldText.builder()
            .label("recordingunit.field.comments")
            .isSystemField(true)
            .id(-415L)
            .isTextArea(true)
            .valueBinding("comments")
            .concept(commentsConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode chronologicalAttributionField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.chronologicalAttribution")
            .isSystemField(true)
            .id(-416L)
            .valueBinding("chronologicalAttribution")
            .concept(chronologicalAttributionConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldInteger taqField =  CustomFieldInteger.builder()
            .label("specimen.field.taq")
            .isSystemField(true)
            .id(-417L)
            .valueBinding("taq")
            .maxValue(Integer.MAX_VALUE)
            .minValue(0)
            .concept(taqConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldInteger tpqField =  CustomFieldInteger.builder()
            .label("specimen.field.tpq")
            .isSystemField(true)
            .id(-418L)
            .maxValue(Integer.MAX_VALUE)
            .minValue(Integer.MIN_VALUE)
            .valueBinding("tpq")
            .concept(tpqConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldInteger numberOfElementField =  CustomFieldInteger.builder()
            .label("specimen.field.numberOfElement")
            .isSystemField(true)
            .id(-419L)
            .maxValue(Integer.MAX_VALUE)
            .minValue(0)
            .valueBinding("numberOfElements")
            .concept(numberOfElementConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldMeasurement weightField =  CustomFieldMeasurement.builder()
            .label("specimen.field.weight")
            .isSystemField(true)
            .id(-420L)
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
            .id(-421L)
            .valueBinding("containers")
            .concept(containerConcept)
            .build();

    protected static Concept phasesConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("specimen.phases")
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultiplePhase phasesField = CustomFieldSelectMultiplePhase.builder()
            .label("specimen.field.phases")
            .isSystemField(true)
            .id(-422L)
            .valueBinding("phases")
            .concept(phasesConcept)
            .build();

    protected static Concept actionUnitConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("specimen.actionUnit")
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneActionUnit actionUnitField = CustomFieldSelectOneActionUnit.builder()
            .label("specimen.field.actionUnit")
            .isSystemField(true)
            .id(-423L)
            .valueBinding("actionUnit")
            .concept(actionUnitConcept)
            .build();



}
