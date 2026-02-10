package fr.siamois.domain.models.actionunit.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.*;
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

    // end date
    @Transient
    @JsonIgnore
    public static final Concept ACTION_CODE_CONCEPT = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4287547")
            .build();


    // --------------- Fields
    @Transient
    @JsonIgnore
    public static final CustomFieldSelectOneFromFieldCode ACTION_UNIT_TYPE_FIELD = CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.category")
            .isSystemField(true)
            .id(4L)
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
            .id(2L)
            .valueBinding("name")
            .concept(NAME_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomFieldText IDENTIFIER_FIELD = CustomFieldText.builder()
            .label("common.label.identifier")
            .id(1L)
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
            .id(3L)
            .valueBinding("spatialContext")
            .concept(SPATIAL_CONTEXT_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldDateTime BEGIN_DATE_FIELD =  CustomFieldDateTime.builder()
            .label("common.field.beginDate")
            .isSystemField(true)
            .valueBinding("beginDate")
            .id(5L)
            .showTime(false)
            .concept(BEGIN_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldDateTime END_DATE_FIELD =  CustomFieldDateTime.builder()
            .label("common.field.endDate")
            .isSystemField(true)
            .valueBinding("endDate")
            .id(7L)
            .showTime(false)
            .concept(END_DATE_CONCEPT)
            .build();

    @Transient
    @JsonIgnore
    protected static final CustomFieldSelectOneActionCode ACTION_CODE_FIELD = CustomFieldSelectOneActionCode.builder()
            .label("actionunit.field.actionCode")
            .isSystemField(true)
            .id(8L)
            .valueBinding("primaryActionCode")
            .concept(ACTION_CODE_CONCEPT)
            .build();

    protected static final  CustomFieldSelectOneSpatialUnit MAIN_LOCATION_FIELD = CustomFieldSelectOneSpatialUnit.builder()
            .label("common.label.mainLocation")
            .isSystemField(true)
            .id(10L)
            .valueBinding("mainLocation")
            .concept(MAIN_LOCATION_CONCEPT)
            .build();



}
