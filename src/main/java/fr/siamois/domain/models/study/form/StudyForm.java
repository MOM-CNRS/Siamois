package fr.siamois.domain.models.study.form;

import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.specimen.CustomFieldSelectMultipleSpecimen;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Transient;

import java.util.List;

import static fr.siamois.ui.form.FormLayoutConstants.SYSTEM_THESO;

/**
 * The study table's system fields: identification, type, the links to recording units and finds, and
 * the columns of the "traitement" sheet of the national reference of the SDA. Which of them a given
 * type uses is configured per type (see {@link StudyDefaults}). Ids are stable negative numbers in the
 * -801…-830 range.
 */
public abstract class StudyForm {

    private static final String LABEL_PREFIX = "study.field.";
    private static final String B_ACTION_UNIT = "actionUnit";
    private static final String B_TYPE = "type";
    private static final String B_METHOD = "method";
    private static final String B_STUDY_DATE = "studyDate";
    private static final String B_RECORDING_UNITS = "recordingUnits";
    private static final String B_SPECIMENS = "specimens";
    private static final String B_TREATMENT_TYPE = "treatmentType";
    private static final String B_TREATMENT_START = "treatmentStart";
    private static final String B_TREATMENT_END = "treatmentEnd";

    /** Bindings of the fields shared by the study of finds and the study of recording units. */
    public static final List<String> LINK_BINDINGS = List.of(B_RECORDING_UNITS, B_SPECIMENS);

    /** Bindings of the fields of the "traitement" sheet. */
    public static final List<String> TREATMENT_BINDINGS = List.of(
            B_TREATMENT_TYPE, "reportCode", B_TREATMENT_START, B_TREATMENT_END,
            "treatmentPlace", "treatmentManager", "treatmentIdentifier", "treatmentComments");

    protected StudyForm() {}

    private static Concept concept(String externalId) {
        return new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("study." + externalId).build();
    }

    private static CustomFieldText text(long id, String binding, boolean textArea) {
        return CustomFieldText.builder()
                .label(LABEL_PREFIX + binding)
                .isSystemField(true)
                .isTextArea(textArea)
                .id(id)
                .valueBinding(binding)
                .concept(concept(binding))
                .build();
    }

    private static CustomFieldSelectOneFromFieldCode one(long id, String binding, String fieldCode) {
        return CustomFieldSelectOneFromFieldCode.builder()
                .label(LABEL_PREFIX + binding)
                .isSystemField(true)
                .id(id)
                .valueBinding(binding)
                .fieldCode(fieldCode)
                .concept(concept(binding))
                .build();
    }

    private static CustomFieldDateTime date(long id, String binding) {
        return CustomFieldDateTime.builder()
                .label(LABEL_PREFIX + binding)
                .isSystemField(true)
                .id(id)
                .valueBinding(binding)
                .showTime(false)
                .concept(concept(binding))
                .build();
    }

    @Transient
    protected static final CustomFieldText identifierField = text(-801L, "identifier", false);

    @Transient
    protected static final CustomFieldSelectOneActionUnit actionUnitField = CustomFieldSelectOneActionUnit.builder()
            .label(LABEL_PREFIX + B_ACTION_UNIT)
            .isSystemField(true)
            .id(-802L)
            .valueBinding(B_ACTION_UNIT)
            .concept(concept(B_ACTION_UNIT))
            .build();

    /** The study's type: the field that selects the form configuration. */
    @Transient
    protected static final CustomFieldSelectOneFromFieldCode typeField = CustomFieldSelectOneFromFieldCode.builder()
            .label(LABEL_PREFIX + B_TYPE)
            .isSystemField(true)
            .id(-803L)
            .valueBinding(B_TYPE)
            .fieldCode(Study.TYPE_FIELD)
            .styleClass("mr-2 study-type-chip")
            .concept(concept(B_TYPE))
            .build();

    @Transient
    protected static final CustomFieldSelectOneFromFieldCode methodField =
            one(-804L, B_METHOD, Study.METHOD_FIELD_CODE);

    @Transient
    protected static final CustomFieldDateTime studyDateField = date(-805L, B_STUDY_DATE);

    // The links to the other entities: optional and multiple, written from the study's side.

    @Transient
    protected static final CustomFieldSelectMultipleRecordingUnit recordingUnitsField = CustomFieldSelectMultipleRecordingUnit.builder()
            .label(LABEL_PREFIX + B_RECORDING_UNITS)
            .isSystemField(true)
            .id(-806L)
            .valueBinding(B_RECORDING_UNITS)
            .concept(concept(B_RECORDING_UNITS))
            .build();

    @Transient
    protected static final CustomFieldSelectMultipleSpecimen specimensField = CustomFieldSelectMultipleSpecimen.builder()
            .label(LABEL_PREFIX + B_SPECIMENS)
            .isSystemField(true)
            .id(-807L)
            .valueBinding(B_SPECIMENS)
            .concept(concept(B_SPECIMENS))
            .build();

    // The columns of the "traitement" sheet. code_OA_NAT and code_element_traite are not fields: they
    // are the project's operation code and the code of the linked find, read at export time.

    @Transient
    protected static final CustomFieldSelectOneFromFieldCode treatmentTypeField =
            one(-808L, B_TREATMENT_TYPE, Study.TREATMENT_TYPE_FIELD_CODE);

    @Transient
    protected static final CustomFieldText reportCodeField = text(-809L, "reportCode", false);

    @Transient
    protected static final CustomFieldDateTime treatmentStartField = date(-810L, B_TREATMENT_START);

    @Transient
    protected static final CustomFieldDateTime treatmentEndField = date(-811L, B_TREATMENT_END);

    @Transient
    protected static final CustomFieldText treatmentPlaceField = text(-812L, "treatmentPlace", false);

    @Transient
    protected static final CustomFieldText treatmentManagerField = text(-813L, "treatmentManager", false);

    @Transient
    protected static final CustomFieldText treatmentIdentifierField = text(-814L, "treatmentIdentifier", false);

    @Transient
    protected static final CustomFieldText treatmentCommentsField = text(-815L, "treatmentComments", true);

    /**
     * The table's system fields, in their default order, with the properties intrinsic to each.
     * This is the field set everything reads; layouts (groups, order, widths) live in configuration.
     */
    public static List<SystemFieldSpec> systemFields() {
        return List.of(
                // The identifier is shown in the header; the project is fixed once the study exists.
                SystemFieldSpec.hiddenReadOnly(identifierField),
                SystemFieldSpec.hiddenReadOnly(actionUnitField),
                SystemFieldSpec.hidden(typeField),
                SystemFieldSpec.of(methodField),
                SystemFieldSpec.of(studyDateField),
                SystemFieldSpec.of(recordingUnitsField),
                SystemFieldSpec.of(specimensField),
                SystemFieldSpec.of(treatmentTypeField),
                SystemFieldSpec.of(reportCodeField),
                SystemFieldSpec.of(treatmentStartField),
                SystemFieldSpec.of(treatmentEndField),
                SystemFieldSpec.of(treatmentPlaceField),
                SystemFieldSpec.of(treatmentManagerField),
                SystemFieldSpec.of(treatmentIdentifierField),
                SystemFieldSpec.of(treatmentCommentsField));
    }
}
