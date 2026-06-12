package fr.siamois.domain.models.container.form;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Transient;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

public abstract class ContainerForm {

    protected ContainerForm() {}

    // --- Concepts ---

    protected static final Concept identifierConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("container.identifier")
            .build();

    protected static final Concept typeConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("container.type")
            .build();

    protected static final Concept spatialUnitConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("container.spatialUnit")
            .build();

    protected static final Concept lengthConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("container.length")
            .build();

    protected static final Concept widthConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("container.width")
            .build();

    protected static final Concept heightConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("container.height")
            .build();

    protected static final Concept weightConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("container.weight")
            .build();

    // --- Fields ---

    @Transient
    protected static final CustomFieldText identifierField = CustomFieldText.builder()
            .label("container.field.identifier")
            .isSystemField(true)
            .isTextArea(false)
            .id(1L)
            .valueBinding("identifier")
            .concept(identifierConcept)
            .build();

    @Transient
    protected static final CustomFieldSelectOneFromFieldCode typeField = CustomFieldSelectOneFromFieldCode.builder()
            .label("container.field.type")
            .isSystemField(true)
            .id(2L)
            .valueBinding("type")
            .fieldCode(Container.TYPE_FIELD)
            .styleClass("mr-2 container-type-chip")
            .concept(typeConcept)
            .build();

    @Transient
    protected static final CustomFieldSelectOneSpatialUnit spatialUnitField = CustomFieldSelectOneSpatialUnit.builder()
            .label("container.field.spatialUnit")
            .isSystemField(true)
            .id(3L)
            .valueBinding("spatialUnit")
            .concept(spatialUnitConcept)
            .build();

    public static final String CENTRIMETRE = "Centimètre";
    @Transient
    protected static final CustomFieldMeasurement lengthField = CustomFieldMeasurement.builder()
            .label("container.field.length")
            .isSystemField(true)
            .id(4L)
            .valueBinding("length")
            .unit(new UnitDefinition(null, null, CENTRIMETRE, "cm", UnitDefinition.Dimension.LENGTH, 0.01, false))
            .concept(lengthConcept)
            .build();

    @Transient
    protected static final CustomFieldMeasurement widthField = CustomFieldMeasurement.builder()
            .label("container.field.width")
            .isSystemField(true)
            .id(5L)
            .valueBinding("width")
            .unit(new UnitDefinition(null, null, CENTRIMETRE, "cm", UnitDefinition.Dimension.LENGTH, 0.01, false))
            .concept(widthConcept)
            .build();

    @Transient
    protected static final CustomFieldMeasurement heightField = CustomFieldMeasurement.builder()
            .label("container.field.height")
            .isSystemField(true)
            .id(6L)
            .valueBinding("height")
            .unit(new UnitDefinition(null, null, CENTRIMETRE, "cm", UnitDefinition.Dimension.LENGTH, 0.01, false))
            .concept(heightConcept)
            .build();

    @Transient
    protected static final CustomFieldMeasurement weightField = CustomFieldMeasurement.builder()
            .label("container.field.weight")
            .isSystemField(true)
            .id(7L)
            .valueBinding("weight")
            .unit(new UnitDefinition(null, null, "Kilogramme", "kg", UnitDefinition.Dimension.MASS, 1000.0, false))
            .concept(weightConcept)
            .build();
}
