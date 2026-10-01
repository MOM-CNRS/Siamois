package fr.siamois.domain.models.phase.form;


import java.util.List;
import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Transient;

import static fr.siamois.ui.form.FormLayoutConstants.SYSTEM_THESO;

public abstract class PhaseForm {

    protected PhaseForm() {}

    protected static final Concept identifierConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.identifier").build();
    protected static final Concept typeConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.type").build();
    protected static final Concept titleConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.title").build();
    protected static final Concept descriptionConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.description").build();
    protected static final Concept orderNumberConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.orderNumber").build();
    protected static final Concept lowerBoundConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.lowerBound").build();
    protected static final Concept upperBoundConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.upperBound").build();
    protected static final Concept periodsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.periods").build();
    protected static final Concept keywordsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.keywords").build();
    protected static final Concept actionUnitConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.actionUnit").build();
    protected static final Concept recordingUnitsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("phase.recordingUnits").build();

    @Transient
    protected static final CustomFieldText identifierField = CustomFieldText.builder()
            .label("phase.field.identifier")
            .isSystemField(true)
            .isTextArea(false)
            .id(-501L)
            .valueBinding("identifier")
            .concept(identifierConcept)
            .build();

    @Transient
    protected static final CustomFieldSelectOneFromFieldCode typeField = CustomFieldSelectOneFromFieldCode.builder()
            .label("phase.field.type")
            .isSystemField(true)
            .id(-502L)
            .valueBinding("type")
            .fieldCode(Phase.TYPE_FIELD)
            .styleClass("mr-2 phase-type-chip")
            .concept(typeConcept)
            .build();

    @Transient
    protected static final CustomFieldText titleField = CustomFieldText.builder()
            .label("phase.field.title")
            .isSystemField(true)
            .isTextArea(false)
            .id(-503L)
            .valueBinding("title")
            .concept(titleConcept)
            .build();

    @Transient
    protected static final CustomFieldText descriptionField = CustomFieldText.builder()
            .label("phase.field.description")
            .isSystemField(true)
            .isTextArea(true)
            .id(-504L)
            .valueBinding("description")
            .concept(descriptionConcept)
            .build();

    @Transient
    protected static final CustomFieldInteger orderNumberField = CustomFieldInteger.builder()
            .label("phase.field.orderNumber")
            .isSystemField(true)
            .id(-505L)
            .minValue(0)
            .maxValue(Integer.MAX_VALUE)
            .valueBinding("orderNumber")
            .concept(orderNumberConcept)
            .build();

    @Transient
    protected static final CustomFieldInteger lowerBoundField = CustomFieldInteger.builder()
            .label("phase.field.lowerBound")
            .isSystemField(true)
            .id(-506L)
            .minValue(Integer.MIN_VALUE)
            .maxValue(Integer.MAX_VALUE)
            .valueBinding("lowerBound")
            .concept(lowerBoundConcept)
            .build();

    @Transient
    protected static final CustomFieldInteger upperBoundField = CustomFieldInteger.builder()
            .label("phase.field.upperBound")
            .isSystemField(true)
            .id(-507L)
            .minValue(Integer.MIN_VALUE)
            .maxValue(Integer.MAX_VALUE)
            .valueBinding("upperBound")
            .concept(upperBoundConcept)
            .build();

    @Transient
    protected static final CustomFieldSelectMultipleFromFieldCode periodsField = CustomFieldSelectMultipleFromFieldCode.builder()
            .label("phase.field.periods")
            .isSystemField(true)
            .id(-508L)
            .valueBinding("periods")
            .fieldCode(Phase.PERIOD_FIELD)
            .concept(periodsConcept)
            .build();

    @Transient
    protected static final CustomFieldSelectMultipleFromFieldCode keywordsField = CustomFieldSelectMultipleFromFieldCode.builder()
            .label("phase.field.keywords")
            .isSystemField(true)
            .id(-509L)
            .valueBinding("keywords")
            .fieldCode(Phase.KEYWORD_FIELD)
            .concept(keywordsConcept)
            .build();

    @Transient
    protected static final CustomFieldSelectOneActionUnit actionUnitField = CustomFieldSelectOneActionUnit.builder()
            .label("phase.field.actionUnit")
            .isSystemField(true)
            .id(-510L)
            .valueBinding("actionUnit")
            .concept(actionUnitConcept)
            .build();

    // The inverse of a recording unit's phases (recording_unit_phase): read-only here, written from
    // the recording unit's side.
    @Transient
    protected static final CustomFieldSelectMultipleRecordingUnit recordingUnitsField = CustomFieldSelectMultipleRecordingUnit.builder()
            .label("phase.field.recordingUnits")
            .isSystemField(true)
            .id(-511L)
            .valueBinding("recordingUnits")
            .concept(recordingUnitsConcept)
            .build();

    /**
     * The table's system fields, in their default order, with the properties intrinsic to each.
     * This is the field set everything reads; layouts (groups, order, widths) live in configuration.
     */
    public static List<SystemFieldSpec> systemFields() {
        return List.of(
            SystemFieldSpec.hiddenReadOnly(identifierField),
            SystemFieldSpec.of(typeField),
            SystemFieldSpec.of(titleField),
            SystemFieldSpec.hiddenReadOnly(actionUnitField),
            SystemFieldSpec.of(orderNumberField),
            SystemFieldSpec.of(keywordsField),
            SystemFieldSpec.of(descriptionField),
            SystemFieldSpec.readOnly(recordingUnitsField),
            SystemFieldSpec.of(periodsField),
            SystemFieldSpec.of(lowerBoundField),
            SystemFieldSpec.of(upperBoundField));
    }
}
