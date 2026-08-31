package fr.siamois.domain.models.actionunit.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity;
import jakarta.persistence.Transient;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

public abstract class ActionUnitForm {

    protected static final String GENERAL_LABEL_CODE = "common.header.general";
    protected static final String SPATIAL_UNIT_CONTEXT_LABEL_CODE = "common.label.spatialContext";
    protected static final String DETAIL_TAB_NAME = "\"Details tab form\"";

    protected ActionUnitForm() {

    }

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

    public static final Concept MAIN_LOCATION_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4288509")
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

    // --------------- Fields
    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneFromFieldCode ACTION_UNIT_TYPE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.category")
            .isSystemField(true)
            .id(-101L)
            .valueBinding("type")
            .styleClass("mr-2 action-unit-type-chip")
            .iconClass("bi bi-bucket")
            .fieldCode(ActionUnit.TYPE_FIELD_CODE)
            .concept(ACTION_UNIT_TYPE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText NAME_FIELD = CustomFieldText.builder()
            .label("common.label.name")
            .isSystemField(true)
            .id(-102L)
            .valueBinding("name")
            .concept(NAME_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText IDENTIFIER_FIELD = CustomFieldText.builder()
            .label("common.label.identifier")
            .id(-103L)
            .isSystemField(true)
            .autoGenerationFunction(AbstractSingleEntity::generateRandomActionUnitIdentifier)
            .valueBinding("identifier")
            .concept(IDENTIFIER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldSelectMultipleSpatialUnitTree SPATIAL_CONTEXT_FIELD = CustomFieldSelectMultipleSpatialUnitTree.builder()
            .label("common.label.selectedSpatialUnits")
            .isSystemField(true)
            .id(-104L)
            .valueBinding("spatialContext")
            .source("GEOPLAT")
            .concept(SPATIAL_CONTEXT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldDateTime BEGIN_DATE_FIELD =  CustomFieldDateTime.builder()
            .label("common.field.beginDate")
            .isSystemField(true)
            .valueBinding("beginDate")
            .id(-105L)
            .showTime(false)
            .concept(BEGIN_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldDateTime END_DATE_FIELD =  CustomFieldDateTime.builder()
            .label("common.field.endDate")
            .isSystemField(true)
            .valueBinding("endDate")
            .id(-106L)
            .showTime(false)
            .concept(END_DATE_CONCEPT)
            .build();

    public static final CustomFieldSelectOneSpatialUnit MAIN_LOCATION_FIELD = CustomFieldSelectOneSpatialUnit.builder()
            .label("common.label.mainLocation")
            .isSystemField(true)
            .id(-108L)
            .source("INSEE")
            .valueBinding("mainLocation")
            .concept(MAIN_LOCATION_CONCEPT)
            .build();

    // --------------- Documentation / administrative concepts (thesaurus th230, TBL_action)

    @Transient
    @JsonIgnore
    public static final Concept OA_CODE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290928").build();

    @Transient
    @JsonIgnore
    public static final Concept PRESCRIPTION_ORDER_NUMBER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290929").build();

    @Transient
    @JsonIgnore
    public static final Concept PRESCRIPTION_ORDER_DATE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290930").build();

    @Transient
    @JsonIgnore
    public static final Concept SCIENTIFIC_MANAGER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290931").build();

    @Transient
    @JsonIgnore
    public static final Concept HOST_STRUCTURE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290932").build();

    @Transient
    @JsonIgnore
    public static final Concept DEVELOPER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290933").build();

    @Transient
    @JsonIgnore
    public static final Concept PERIODS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290934").build();

    @Transient
    @JsonIgnore
    public static final Concept SUBJECTS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290935").build();

    @Transient
    @JsonIgnore
    public static final Concept SCIENTIFIC_NOTICE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290936").build();

    @Transient
    @JsonIgnore
    public static final Concept STATUS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290937").build();

    @Transient
    @JsonIgnore
    public static final Concept COMMENTS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4289279").build();

    @Transient
    @JsonIgnore
    public static final Concept SYSTEM_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290938").build();

    @Transient
    @JsonIgnore
    public static final Concept FIELD_STATUS_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290939").build();

    @Transient
    @JsonIgnore
    public static final Concept ZMIN_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290940").build();

    @Transient
    @JsonIgnore
    public static final Concept ZMAX_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290941").build();

    @Transient
    @JsonIgnore
    public static final Concept DESIGNATION_ORDER_NUMBER_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290942").build();

    @Transient
    @JsonIgnore
    public static final Concept DESIGNATION_ORDER_DATE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290943").build();

    @Transient
    @JsonIgnore
    public static final Concept EXCAVATED_AREA_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290944").build();

    @Transient
    @JsonIgnore
    public static final Concept ACCESSIBLE_AREA_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290945").build();

    @Transient
    @JsonIgnore
    public static final Concept OPENING_RATE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290946").build();

    @Transient
    @JsonIgnore
    public static final Concept DEVELOPMENT_NATURE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290947").build();

    @Transient
    @JsonIgnore
    public static final Concept VOLUME_COUNT_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290948").build();

    @Transient
    @JsonIgnore
    public static final Concept PAGE_COUNT_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290949").build();

    @Transient
    @JsonIgnore
    public static final Concept FIGURE_COUNT_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290950").build();

    @Transient
    @JsonIgnore
    public static final Concept APPENDIX_COUNT_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290951").build();

    @Transient
    @JsonIgnore
    public static final Concept PRESCRIBED_AREA_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO).externalId("4290952").build();

    // --------------- Documentation / administrative fields

    @Transient
    @JsonIgnore
    public static final CustomFieldText OA_CODE_FIELD = CustomFieldText.builder()
            .label("actionunit.field.oaCode")
            .isSystemField(true)
            .id(-109L)
            .valueBinding("oaCode")
            .concept(OA_CODE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText PRESCRIPTION_ORDER_NUMBER_FIELD = CustomFieldText.builder()
            .label("actionunit.field.prescriptionOrderNumber")
            .isSystemField(true)
            .id(-110L)
            .valueBinding("prescriptionOrderNumber")
            .concept(PRESCRIPTION_ORDER_NUMBER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldDateTime PRESCRIPTION_ORDER_DATE_FIELD = CustomFieldDateTime.builder()
            .label("actionunit.field.prescriptionOrderDate")
            .isSystemField(true)
            .id(-111L)
            .showTime(false)
            .valueBinding("prescriptionOrderDate")
            .concept(PRESCRIPTION_ORDER_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText SCIENTIFIC_MANAGER_FIELD = CustomFieldText.builder()
            .label("actionunit.field.scientificManager")
            .isSystemField(true)
            .id(-112L)
            .valueBinding("scientificManager")
            .concept(SCIENTIFIC_MANAGER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText HOST_STRUCTURE_FIELD = CustomFieldText.builder()
            .label("actionunit.field.hostStructure")
            .isSystemField(true)
            .id(-113L)
            .valueBinding("hostStructure")
            .concept(HOST_STRUCTURE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText DEVELOPER_FIELD = CustomFieldText.builder()
            .label("actionunit.field.developer")
            .isSystemField(true)
            .id(-114L)
            .valueBinding("developer")
            .concept(DEVELOPER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldSelectMultipleFromFieldCode PERIODS_FIELD = CustomFieldSelectMultipleFromFieldCode.builder()
            .label("actionunit.field.periods")
            .isSystemField(true)
            .id(-115L)
            .valueBinding("periods")
            .fieldCode(ActionUnit.PERIODS_FIELD_CODE)
            .concept(PERIODS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldSelectMultipleFromFieldCode SUBJECTS_FIELD = CustomFieldSelectMultipleFromFieldCode.builder()
            .label("actionunit.field.subjects")
            .isSystemField(true)
            .id(-116L)
            .valueBinding("subjects")
            .fieldCode(ActionUnit.SUBJECTS_FIELD_CODE)
            .concept(SUBJECTS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText SCIENTIFIC_NOTICE_FIELD = CustomFieldText.builder()
            .label("actionunit.field.scientificNotice")
            .isSystemField(true)
            .id(-117L)
            .isTextArea(true)
            .valueBinding("scientificNotice")
            .concept(SCIENTIFIC_NOTICE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneFromFieldCode STATUS_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("actionunit.field.status")
            .isSystemField(true)
            .id(-118L)
            .valueBinding("status")
            .fieldCode(ActionUnit.STATUS_FIELD_CODE)
            .concept(STATUS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText COMMENTS_FIELD = CustomFieldText.builder()
            .label("common.field.comments")
            .isSystemField(true)
            .id(-119L)
            .isTextArea(true)
            .valueBinding("comments")
            .concept(COMMENTS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneFromFieldCode SYSTEM_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("actionunit.field.system")
            .isSystemField(true)
            .id(-120L)
            .valueBinding("system")
            .fieldCode(ActionUnit.SYSTEM_FIELD_CODE)
            .concept(SYSTEM_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneFromFieldCode FIELD_STATUS_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("actionunit.field.fieldStatus")
            .isSystemField(true)
            .id(-121L)
            .valueBinding("fieldStatus")
            .fieldCode(ActionUnit.FIELD_STATUS_FIELD_CODE)
            .concept(FIELD_STATUS_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldDecimal ZMIN_FIELD = CustomFieldDecimal.builder()
            .label("actionunit.field.zmin")
            .isSystemField(true)
            .id(-122L)
            .valueBinding("zmin")
            .concept(ZMIN_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldDecimal ZMAX_FIELD = CustomFieldDecimal.builder()
            .label("actionunit.field.zmax")
            .isSystemField(true)
            .id(-123L)
            .valueBinding("zmax")
            .concept(ZMAX_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText DESIGNATION_ORDER_NUMBER_FIELD = CustomFieldText.builder()
            .label("actionunit.field.designationOrderNumber")
            .isSystemField(true)
            .id(-124L)
            .valueBinding("designationOrderNumber")
            .concept(DESIGNATION_ORDER_NUMBER_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldDateTime DESIGNATION_ORDER_DATE_FIELD = CustomFieldDateTime.builder()
            .label("actionunit.field.designationOrderDate")
            .isSystemField(true)
            .id(-125L)
            .showTime(false)
            .valueBinding("designationOrderDate")
            .concept(DESIGNATION_ORDER_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldDecimal EXCAVATED_AREA_FIELD = CustomFieldDecimal.builder()
            .label("actionunit.field.excavatedArea")
            .isSystemField(true)
            .id(-126L)
            .valueBinding("excavatedArea")
            .concept(EXCAVATED_AREA_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldDecimal ACCESSIBLE_AREA_FIELD = CustomFieldDecimal.builder()
            .label("actionunit.field.accessibleArea")
            .isSystemField(true)
            .id(-127L)
            .valueBinding("accessibleArea")
            .concept(ACCESSIBLE_AREA_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldDecimal OPENING_RATE_FIELD = CustomFieldDecimal.builder()
            .label("actionunit.field.openingRate")
            .isSystemField(true)
            .id(-128L)
            .valueBinding("openingRate")
            .concept(OPENING_RATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneFromFieldCode DEVELOPMENT_NATURE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("actionunit.field.developmentNature")
            .isSystemField(true)
            .id(-129L)
            .valueBinding("developmentNature")
            .fieldCode(ActionUnit.DEVELOPMENT_NATURE_FIELD_CODE)
            .concept(DEVELOPMENT_NATURE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldInteger VOLUME_COUNT_FIELD = CustomFieldInteger.builder()
            .label("actionunit.field.volumeCount")
            .isSystemField(true)
            .id(-130L)
            .valueBinding("volumeCount")
            .concept(VOLUME_COUNT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldInteger PAGE_COUNT_FIELD = CustomFieldInteger.builder()
            .label("actionunit.field.pageCount")
            .isSystemField(true)
            .id(-131L)
            .valueBinding("pageCount")
            .concept(PAGE_COUNT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldInteger FIGURE_COUNT_FIELD = CustomFieldInteger.builder()
            .label("actionunit.field.figureCount")
            .isSystemField(true)
            .id(-132L)
            .valueBinding("figureCount")
            .concept(FIGURE_COUNT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldInteger APPENDIX_COUNT_FIELD = CustomFieldInteger.builder()
            .label("actionunit.field.appendixCount")
            .isSystemField(true)
            .id(-133L)
            .valueBinding("appendixCount")
            .concept(APPENDIX_COUNT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldDecimal PRESCRIBED_AREA_FIELD = CustomFieldDecimal.builder()
            .label("actionunit.field.prescribedArea")
            .isSystemField(true)
            .id(-134L)
            .valueBinding("prescribedArea")
            .concept(PRESCRIBED_AREA_CONCEPT)
            .build();

}
