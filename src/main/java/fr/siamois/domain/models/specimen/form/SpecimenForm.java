package fr.siamois.domain.models.specimen.form;


import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldBoolean;
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

import java.util.List;
import java.util.Map;

import static fr.siamois.ui.form.FormLayoutConstants.SYSTEM_THESO;

public abstract class SpecimenForm {

    protected SpecimenForm() {}

    // ----------- Concepts for system fields

    // Specimen identifier
    protected static Concept specimenIdConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1389")
            .build();

    // Specimen identifier
    protected static Concept specimenOtherIdConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1390")
            .build();

    // Authors
    @Transient
    @JsonIgnore
    protected static Concept authorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1395")
            .build();

    // Excavators
    @Transient
    @JsonIgnore
    protected static Concept collectorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1396")
            .build();

    // Specimen type
    @Transient
    @JsonIgnore
    protected static Concept specimenTypeConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1387")
            .build();

    // Date
    @Transient
    @JsonIgnore
    protected static Concept collectionDateConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1397")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept recordingUnitConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1392")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept isPartOfConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1393")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept containsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1394")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept isolationNumberConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1391")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept containerConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1410")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept materialConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1398")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept descriptionConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1402")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept commentsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1403")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept materialClassConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1399")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept normalizedInterpretationConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1400")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept taqConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1406")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept tpqConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1405")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept chronologicalAttributionConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1404")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept numberOfElementConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1407")
            .build();

    @Transient
    @JsonIgnore
    protected static Concept weightConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("1408")
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
    protected static CustomFieldSelectOneFromFieldCode specimenTypeField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.type")
            .isSystemField(true)
            .valueBinding("type")
            .id(-409L)
            .styleClass("mr-2 specimen-type-chip")
            .iconClass("bi bi-bucket")
            .fieldCode(Specimen.TYPE_FIELD)
            .concept(specimenTypeConcept)
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
            .fieldCode(Specimen.CHRONOLOGICAL_ATTRIBUTION_FIELD)
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
            .externalId("1411")
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
            .externalId("1412")
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

    // ----------- Fields of the national report inventory (no concept in the system thesaurus yet)

    private static final String B_OBJECT_OR_LOT = "objectOrLot";
    private static final String B_STUDY_POTENTIAL = "studyPotential";
    private static final String B_VALIDITY_DATE = "validityDate";
    private static final String B_STORAGE_LOCATION = "storageLocation";

    /** Concept of each of these fields in the system thesaurus, by binding. */
    private static final Map<String, String> CONCEPT_IDS = Map.ofEntries(
            Map.entry(B_OBJECT_OR_LOT, "1388"),
            Map.entry("samplingNature", "1401"),
            Map.entry("sanitaryState", "1413"),
            Map.entry("sanitaryAlert", "1414"),
            Map.entry("transferStatus", "1415"),
            Map.entry("selectiveConservation", "1416"),
            Map.entry("selectiveConservationComment", "1417"),
            Map.entry("particularInterest", "1418"),
            Map.entry(B_STUDY_POTENTIAL, "1419"),
            Map.entry("studyPotentialComment", "1420"),
            Map.entry("samplingPurpose", "1421"),
            Map.entry("preservationCondition", "1422"),
            Map.entry(B_VALIDITY_DATE, "1423"),
            Map.entry(B_STORAGE_LOCATION, "1424"));

    private static Concept symbolicConcept(String binding) {
        String id = CONCEPT_IDS.getOrDefault(binding, "specimen." + binding);
        return new Concept.Builder().vocabulary(SYSTEM_THESO).externalId(id).build();
    }

    private static CustomFieldText textArea(long id, String binding) {
        return CustomFieldText.builder()
                .label("specimen.field." + binding)
                .isSystemField(true)
                .id(id)
                .isTextArea(true)
                .valueBinding(binding)
                .concept(symbolicConcept(binding))
                .build();
    }

    private static CustomFieldSelectOneFromFieldCode selectOne(long id, String binding, String fieldCode) {
        return CustomFieldSelectOneFromFieldCode.builder()
                .label("specimen.field." + binding)
                .isSystemField(true)
                .id(id)
                .fieldCode(fieldCode)
                .valueBinding(binding)
                .concept(symbolicConcept(binding))
                .build();
    }

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode sanitaryStateField = selectOne(-424L, "sanitaryState", Specimen.SANITARY_STATE_FIELD);

    @Transient
    @JsonIgnore
    protected static CustomFieldText sanitaryAlertField = textArea(-425L, "sanitaryAlert");

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode transferStatusField = selectOne(-426L, "transferStatus", Specimen.TRANSFER_STATUS_FIELD);

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode selectiveConservationField =
            selectOne(-427L, "selectiveConservation", Specimen.SELECTIVE_CONSERVATION_FIELD);

    @Transient
    @JsonIgnore
    protected static CustomFieldText selectiveConservationCommentField = textArea(-428L, "selectiveConservationComment");

    @Transient
    @JsonIgnore
    protected static CustomFieldText particularInterestField = textArea(-429L, "particularInterest");

    @Transient
    @JsonIgnore
    protected static CustomFieldBoolean studyPotentialField = CustomFieldBoolean.builder()
            .label("specimen.field.studyPotential")
            .isSystemField(true)
            .id(-430L)
            .valueBinding(B_STUDY_POTENTIAL)
            .concept(symbolicConcept(B_STUDY_POTENTIAL))
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldText studyPotentialCommentField = textArea(-431L, "studyPotentialComment");

    @Transient
    @JsonIgnore
    protected static CustomFieldText samplingPurposeField = textArea(-432L, "samplingPurpose");

    @Transient
    @JsonIgnore
    protected static CustomFieldText preservationConditionField = textArea(-433L, "preservationCondition");

    @Transient
    @JsonIgnore
    protected static CustomFieldDateTime validityDateField = CustomFieldDateTime.builder()
            .label("specimen.field.validityDate")
            .isSystemField(true)
            .id(-434L)
            .valueBinding(B_VALIDITY_DATE)
            .showTime(false)
            .concept(symbolicConcept(B_VALIDITY_DATE))
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldText storageLocationField = CustomFieldText.builder()
            .label("specimen.field.storageLocation")
            .isSystemField(true)
            .id(-435L)
            .valueBinding(B_STORAGE_LOCATION)
            .concept(symbolicConcept(B_STORAGE_LOCATION))
            .build();

    /** The find's geometry, shown as WKT: it is displayed by the form, never edited through it. */
    @Transient
    @JsonIgnore
    protected static CustomFieldText geomField = CustomFieldText.builder()
            .label("common.field.geometry")
            .isSystemField(true)
            .isTextArea(true)
            .id(-438L)
            .valueBinding("geom")
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldBoolean objectOrLotField = CustomFieldBoolean.builder()
            .label("specimen.field.objectOrLot")
            .isSystemField(true)
            .id(-436L)
            .valueBinding(B_OBJECT_OR_LOT)
            .concept(symbolicConcept(B_OBJECT_OR_LOT))
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode samplingNatureField =
            selectOne(-437L, "samplingNature", Specimen.SAMPLING_NATURE_FIELD);

    /**
     * The table's system fields, in their default order, with the properties intrinsic to each.
     * This is the field set everything reads; layouts (groups, order, widths) live in configuration.
     */
    public static List<SystemFieldSpec> systemFields() {
        return List.of(
            SystemFieldSpec.readOnly(recordingUnitField),
            SystemFieldSpec.of(isPartOfField),
            SystemFieldSpec.of(containsField),
            SystemFieldSpec.hiddenReadOnly(specimenIdField),
            SystemFieldSpec.readOnly(actionUnitField),
            SystemFieldSpec.of(specimenOtherIdField),
            SystemFieldSpec.hidden(specimenTypeField),
            SystemFieldSpec.of(isolationNumberField),
            SystemFieldSpec.of(containerField),
            SystemFieldSpec.of(materialField),
            SystemFieldSpec.of(materialClassField),
            SystemFieldSpec.of(normalizedInterpretationField),
            SystemFieldSpec.of(descriptionField),
            SystemFieldSpec.of(commentsField),
            SystemFieldSpec.of(chronologicalAttributionField),
            SystemFieldSpec.of(taqField),
            SystemFieldSpec.of(tpqField),
            SystemFieldSpec.of(phasesField),
            SystemFieldSpec.of(weightField),
            SystemFieldSpec.of(numberOfElementField),
            SystemFieldSpec.of(authorsField),
            SystemFieldSpec.of(collectorsField),
            SystemFieldSpec.of(collectionDateField),
            SystemFieldSpec.of(sanitaryStateField),
            SystemFieldSpec.of(sanitaryAlertField),
            SystemFieldSpec.of(transferStatusField),
            SystemFieldSpec.of(selectiveConservationField),
            SystemFieldSpec.of(selectiveConservationCommentField),
            SystemFieldSpec.of(particularInterestField),
            SystemFieldSpec.of(studyPotentialField),
            SystemFieldSpec.of(studyPotentialCommentField),
            SystemFieldSpec.of(samplingPurposeField),
            SystemFieldSpec.of(preservationConditionField),
            SystemFieldSpec.of(validityDateField),
            SystemFieldSpec.of(storageLocationField),
            SystemFieldSpec.of(objectOrLotField),
            SystemFieldSpec.of(samplingNatureField),
            SystemFieldSpec.readOnly(geomField));
    }
}
