package fr.siamois.domain.models.recordingunit.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.phase.CustomFieldSelectMultiplePhase;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
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
            .id(-301L)
            .valueBinding("author")
            .concept(AUTHOR_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode RECORDING_UNIT_TYPE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.property.type")
            .isSystemField(true)
            .valueBinding("type")
            .id(-302L)
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
            .id(-303L)
            .showTime(false)
            .concept(OPENING_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneSpatialUnit SPATIAL_UNIT_FIELD = CustomFieldSelectOneSpatialUnit.builder()
            .label("recordingunit.field.spatialUnit")
            .isSystemField(true)
            .valueBinding("spatialUnit")
            .id(-304L)
            .concept(SPATIAL_UNIT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneActionUnit ACTION_UNIT_FIELD = CustomFieldSelectOneActionUnit.builder()
            .label("recordingunit.field.actionUnit")
            .isSystemField(true)
            .valueBinding("actionUnit")
            .id(-305L)
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
    protected static final Concept MATRIX_COLOR_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("Couleur de la matrice")
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldText FULL_IDENTIFIER_FIELD = CustomFieldText.builder()
            .label("common.label.identifier")
            .isSystemField(true)
            .id(-306L)
            .valueBinding("fullIdentifier")
            .concept(FULL_IDENTIFIER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode NATURE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.property.geomorpho")
            .isSystemField(true)
            .valueBinding("geomorphologicalCycle")
            .id(-307L)
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
            .id(-308L)
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
            .id(-309L)
            .valueBinding("contributors")
            .concept(CONTRIBUTORS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode GEOMORPHO_AGENT_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.field.geomorphoAgent")
            .isSystemField(true)
            .valueBinding("geomorphologicalAgent")
            .id(-310L)
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
            .id(-311L)
            .fieldCode(RecordingUnit.EROSION_SHAPE_FIELD_CODE)
            .concept(EROSION_SHAPE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode EROSION_PROFILE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.field.erosionProfile")
            .isSystemField(true)
            .valueBinding("erosionProfile")
            .id(-312L)
            .fieldCode(RecordingUnit.EROSION_PROFILE_FIELD_CODE)
            .concept(EROSION_PROFILE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode EROSION_ORIENTATION_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.field.erosionOrientation")
            .isSystemField(true)
            .valueBinding("erosionOrientation")
            .id(-313L)
            .fieldCode(RecordingUnit.EROSION_ORIENTATION_FIELD_CODE)
            .concept(EROSION_ORIENTATION_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneFromFieldCode CHRONOLOGICAL_PHASE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("recordingunit.field.chronologicalPhase")
            .isSystemField(true)
            .valueBinding("chronologicalPhase")
            .id(-314L)
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
            .id(-315L)
            .valueBinding("taq")
            .concept(TAQ_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldInteger TPQ_FIELD = CustomFieldInteger.builder()
            .label("recordingunit.field.tpq")
            .isSystemField(true)
            .id(-316L)
            .valueBinding("tpq")
            .concept(TPQ_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldText DESCRIPTION_FIELD = CustomFieldText.builder()
            .label("recordingunit.field.description")
            .isSystemField(true)
            .id(-317L)
            .valueBinding("description")
            .isTextArea(true)
            .concept(DESCRIPTION_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldDateTime CLOSING_DATE_FIELD = CustomFieldDateTime.builder()
            .label("recordingunit.field.closingDate")
            .isSystemField(true)
            .id(-318L)
            .valueBinding("closingDate")
            .showTime(false)
            .concept(CLOSING_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectMultipleRecordingUnit PARENTS_FIELD = CustomFieldSelectMultipleRecordingUnit.builder()
            .label("common.field.parents")
            .isSystemField(true)
            .id(-319L)
            .valueBinding("parents")
            .concept(PARENTS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectMultipleRecordingUnit CHILDREN_FIELD = CustomFieldSelectMultipleRecordingUnit.builder()
            .label("common.field.children")
            .isSystemField(true)
            .id(-320L)
            .valueBinding("children")
            .concept(CHILDREN_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldText COMMENTS_FIELD = CustomFieldText.builder()
            .label("common.field.comments")
            .isSystemField(true)
            .id(-321L)
            .valueBinding("comments")
            .isTextArea(true)
            .concept(COMMENTS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldMeasurement Z_INF_FIELD = CustomFieldMeasurement.builder()
            .label("recordingunit.property.zInf")
            .isSystemField(true)
            .id(-322L)
            .valueBinding("zInf")
            .unit(new UnitDefinition(null, null, "Mètre", "m", UnitDefinition.Dimension.LENGTH, 1.0, true))
            .concept(Z_INF_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldMeasurement Z_SUP_FIELD = CustomFieldMeasurement.builder()
            .label("recordingunit.property.zSup")
            .isSystemField(true)
            .id(-323L)
            .valueBinding("zSup")
            .unit(new UnitDefinition(null, null, "Mètre", "m", UnitDefinition.Dimension.LENGTH, 1.0, true))
            .concept(Z_SUP_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectMultiplePhase PHASES_FIELD = CustomFieldSelectMultiplePhase.builder()
            .label("recordingunit.field.phases")
            .isSystemField(true)
            .id(-324L)
            .valueBinding("phases")
            .concept(PHASES_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldText MATRIX_COLOR_FIELD = CustomFieldText.builder()
            .label("recordingunit.field.matrixColor")
            .isSystemField(true)
            .isTextArea(false)
            .id(-325L)
            .valueBinding("matrixColor")
            .concept(MATRIX_COLOR_CONCEPT)
            .build();

}
