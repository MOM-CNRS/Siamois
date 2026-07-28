package fr.siamois.domain.models.recordingunit.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Transient;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

/**
 * Shared field/concept constants for {@link RecordingUnitNewForm} and {@link RecordingUnitDetailsForm},
 * mirroring the pattern used by {@code ActionUnitForm} for {@code ActionUnit}.
 */
public abstract class RecordingUnitForm {

    protected static final String COMMON_HEADER_GENERAL = "common.header.general";

    protected RecordingUnitForm() {
    }

    // ----------- Concepts and fields shared with the "new unit" form -----------

    @Transient
    @JsonIgnore
    protected static final Concept AUTHOR_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286194")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept RECORDING_UNIT_TYPE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287605")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept OPENING_DATE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286198")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept SPATIAL_UNIT_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286245")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept ACTION_UNIT_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286244")
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOnePerson AUTHOR_FIELD = CustomFieldSelectOnePerson.builder()
            .label("recordingunit.field.mainAuthor")
            .isSystemField(true)
            .id(1L)
            .valueBinding("author")
            .concept(AUTHOR_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode RECORDING_UNIT_TYPE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.property.type")
            .isSystemField(true)
            .valueBinding("type")
            .id(3L)
            .styleClass(RecordingUnit.MR_2_RECORDING_UNIT_TYPE_CHIP)
            .iconClass(RecordingUnit.BI_BI_PENCIL_SQUARE)
            .fieldCode(RecordingUnit.TYPE_FIELD_CODE)
            .concept(RECORDING_UNIT_TYPE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldDateTime OPENING_DATE_FIELD = CustomFieldDateTime.builder()
            .label("recordingunit.field.openingDate")
            .isSystemField(true)
            .valueBinding("openingDate")
            .id(4L)
            .showTime(false)
            .concept(OPENING_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneSpatialUnit SPATIAL_UNIT_FIELD = CustomFieldSelectOneSpatialUnit.builder()
            .label("recordingunit.field.spatialUnit")
            .isSystemField(true)
            .valueBinding("spatialUnit")
            .id(5L)
            .concept(SPATIAL_UNIT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneActionUnit ACTION_UNIT_FIELD = CustomFieldSelectOneActionUnit.builder()
            .label("recordingunit.field.actionUnit")
            .isSystemField(true)
            .valueBinding("actionUnit")
            .id(6L)
            .concept(ACTION_UNIT_CONCEPT)
            .build();

    // ----------- Concepts and fields used only by the details form -----------

    @Transient
    @JsonIgnore
    protected static final Concept FULL_IDENTIFIER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287640")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept NATURE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287606")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept INTERPRETATION_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286197")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept CONTRIBUTORS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287594")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept GEOMORPHO_AGENT_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287607")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept EROSION_SHAPE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287641")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept EROSION_PROFILE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287642")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept EROSION_ORIENTATION_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287643")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept CHRONOLOGICAL_PHASE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287612")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept TAQ_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287614")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept TPQ_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287613")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept DESCRIPTION_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287611")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept CLOSING_DATE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286199")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept PARENTS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4289277")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept CHILDREN_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4289278")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept COMMENTS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4289279")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept Z_INF_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4289320")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept Z_SUP_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4289321")
            .build();

    @Transient
    @JsonIgnore
    protected static final Concept PHASES_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4290858")
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldText FULL_IDENTIFIER_FIELD = CustomFieldText.builder()
            .label("common.label.identifier")
            .isSystemField(true)
            .id(10L)
            .valueBinding("fullIdentifier")
            .concept(FULL_IDENTIFIER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode NATURE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.property.geomorpho")
            .isSystemField(true)
            .valueBinding("geomorphologicalCycle")
            .id(11L)
            .styleClass(RecordingUnit.MR_2_RECORDING_UNIT_TYPE_CHIP)
            .iconClass(RecordingUnit.BI_BI_PENCIL_SQUARE)
            .fieldCode(RecordingUnit.GEOMORPHO_CYCLE_FIELD_CODE)
            .concept(NATURE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode INTERPRETATION_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.property.interpretation")
            .isSystemField(true)
            .valueBinding("normalizedInterpretation")
            .id(12L)
            .styleClass(RecordingUnit.MR_2_RECORDING_UNIT_TYPE_CHIP)
            .iconClass(RecordingUnit.BI_BI_PENCIL_SQUARE)
            .fieldCode(RecordingUnit.INTERPRETATION_FIELD_CODE)
            .concept(INTERPRETATION_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectMultiplePerson CONTRIBUTORS_FIELD = CustomFieldSelectMultiplePerson.builder()
            .label("recordingunit.field.contributors")
            .isSystemField(true)
            .id(13L)
            .valueBinding("contributors")
            .concept(CONTRIBUTORS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode GEOMORPHO_AGENT_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.field.geomorphoAgent")
            .isSystemField(true)
            .valueBinding("geomorphologicalAgent")
            .id(14L)
            .styleClass(RecordingUnit.MR_2_RECORDING_UNIT_TYPE_CHIP)
            .iconClass(RecordingUnit.BI_BI_PENCIL_SQUARE)
            .fieldCode(RecordingUnit.GEOMORPHO_AGENT_FIELD_CODE)
            .concept(GEOMORPHO_AGENT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode EROSION_SHAPE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.field.erosionShape")
            .isSystemField(true)
            .valueBinding("erosionShape")
            .id(15L)
            .fieldCode(RecordingUnit.EROSION_SHAPE_FIELD_CODE)
            .concept(EROSION_SHAPE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode EROSION_PROFILE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.field.erosionProfile")
            .isSystemField(true)
            .valueBinding("erosionProfile")
            .id(16L)
            .fieldCode(RecordingUnit.EROSION_PROFILE_FIELD_CODE)
            .concept(EROSION_PROFILE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode EROSION_ORIENTATION_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.field.erosionOrientation")
            .isSystemField(true)
            .valueBinding("erosionOrientation")
            .id(17L)
            .fieldCode(RecordingUnit.EROSION_ORIENTATION_FIELD_CODE)
            .concept(EROSION_ORIENTATION_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode CHRONOLOGICAL_PHASE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.field.chronologicalPhase")
            .isSystemField(true)
            .valueBinding("chronologicalPhase")
            .id(18L)
            .styleClass(RecordingUnit.MR_2_RECORDING_UNIT_TYPE_CHIP)
            .iconClass(RecordingUnit.BI_BI_PENCIL_SQUARE)
            .fieldCode("SIARU.CHRONO")
            .concept(CHRONOLOGICAL_PHASE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldInteger TAQ_FIELD = CustomFieldInteger.builder()
            .label("recordingunit.field.taq")
            .isSystemField(true)
            .id(19L)
            .valueBinding("taq")
            .concept(TAQ_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldInteger TPQ_FIELD = CustomFieldInteger.builder()
            .label("recordingunit.field.tpq")
            .isSystemField(true)
            .id(20L)
            .valueBinding("tpq")
            .concept(TPQ_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldText DESCRIPTION_FIELD = CustomFieldText.builder()
            .label("recordingunit.field.description")
            .isSystemField(true)
            .id(21L)
            .valueBinding("description")
            .isTextArea(true)
            .concept(DESCRIPTION_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldDateTime CLOSING_DATE_FIELD = CustomFieldDateTime.builder()
            .label("recordingunit.field.closingDate")
            .isSystemField(true)
            .id(22L)
            .valueBinding("closingDate")
            .showTime(false)
            .concept(CLOSING_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectMultipleRecordingUnit PARENTS_FIELD = CustomFieldSelectMultipleRecordingUnit.builder()
            .label("common.field.parents")
            .isSystemField(true)
            .id(23L)
            .valueBinding("parents")
            .concept(PARENTS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectMultipleRecordingUnit CHILDREN_FIELD = CustomFieldSelectMultipleRecordingUnit.builder()
            .label("common.field.children")
            .isSystemField(true)
            .id(24L)
            .valueBinding("children")
            .concept(CHILDREN_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldText COMMENTS_FIELD = CustomFieldText.builder()
            .label("common.field.comments")
            .isSystemField(true)
            .id(25L)
            .valueBinding("comments")
            .isTextArea(true)
            .concept(COMMENTS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldMeasurement Z_INF_FIELD = CustomFieldMeasurement.builder()
            .label("recordingunit.property.zInf")
            .isSystemField(true)
            .id(26L)
            .valueBinding("zInf")
            .unit(new UnitDefinition(null, null, "Mètre", "m", UnitDefinition.Dimension.LENGTH, 1.0, true))
            .concept(Z_INF_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldMeasurement Z_SUP_FIELD = CustomFieldMeasurement.builder()
            .label("recordingunit.property.zSup")
            .isSystemField(true)
            .id(27L)
            .valueBinding("zSup")
            .unit(new UnitDefinition(null, null, "Mètre", "m", UnitDefinition.Dimension.LENGTH, 1.0, true))
            .concept(Z_SUP_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectMultiplePhase PHASES_FIELD = CustomFieldSelectMultiplePhase.builder()
            .label("recordingunit.field.phases")
            .isSystemField(true)
            .id(28L)
            .valueBinding("phases")
            .concept(PHASES_CONCEPT)
            .build();

}
