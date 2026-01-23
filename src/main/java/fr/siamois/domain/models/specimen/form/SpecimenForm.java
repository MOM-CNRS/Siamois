package fr.siamois.domain.models.specimen.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
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
            .externalId("4286193")
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

    // --------------- Fields

    // Fields
    protected static CustomFieldText specimenIdField = CustomFieldText.builder()
            .label("recordingunit.field.identifier")
            .isSystemField(true)
            .id(1L)
            .valueBinding("fullIdentifier")
            .concept(specimenIdConcept)
            .build();


    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultiplePerson authorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.authors")
            .isSystemField(true)
            .id(2L)
            .valueBinding("authors")
            .concept(authorsConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectMultiplePerson collectorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.collectors")
            .isSystemField(true)
            .id(3L)
            .valueBinding("collectors")
            .concept(collectorsConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode specimenTypeField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.type")
            .isSystemField(true)
            .valueBinding("type")
            .id(4L)
            .styleClass("mr-2 specimen-type-chip")
            .iconClass("bi bi-bucket")
            .fieldCode(Specimen.CATEGORY_FIELD)
            .concept(specimenTypeConcept)
            .build();

    @Transient
    @JsonIgnore
    protected static CustomFieldSelectOneFromFieldCode specimenCategoryField =  CustomFieldSelectOneFromFieldCode.builder()
            .label("specimen.field.category")
            .isSystemField(true)
            .valueBinding("category")
            .id(5L)
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
            .id(6L)
            .valueBinding("collectionDate")
            .showTime(false)
            .concept(collectionDateConcept)
            .build();

    

}
