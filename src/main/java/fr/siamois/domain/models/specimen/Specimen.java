package fr.siamois.domain.models.specimen;


import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.COLUMN_CLASS_NAME;
import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

@Data
@Entity
@Table(name = "specimen")
@Audited
public class Specimen extends SpecimenParent implements ArkEntity {

    public Specimen() {

    }

    public Specimen(Specimen specimen) {
        setType(specimen.getType());
        setRecordingUnit(specimen.getRecordingUnit());
        setCategory(specimen.getCategory());
        setCreatedByInstitution(specimen.getCreatedByInstitution());
        setCreatedBy(specimen.getCreatedBy());
        setAuthors(specimen.getAuthors());
        setCollectors(specimen.getCollectors());
        setCollectionDate(specimen.getCollectionDate());
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "specimen_id", nullable = false)
    private Long id;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "specimen_document",
            joinColumns = {@JoinColumn(name = "fk_specimen_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_document_id")}
    )
    private Set<Document> documents = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "specimen_authors",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_person_id"))
    @NotAudited
    private List<Person> authors;

    @ManyToMany
    @JoinTable(
            name = "specimen_collectors",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_person_id"))
    @NotAudited
    private List<Person> collectors;



    @FieldCode
    public static final String CATEGORY_FIELD = "SIAS.CATEGORY"; // ceramique, ...

    @FieldCode
    public static final String METHOD_FIELD = "SIAS.METHOD";

    @FieldCode
    public static final String CAT_FIELD = "SIAS.CAT"; // lot, individu, echantillon

    @FieldCode
    public static final String MATIERE_FIELD = "SIAS.MATIERE";

    @FieldCode
    public static final String INTERPRETATION_FIELD = "SIAS.INTERPRETATION";

    @Transient
    @JsonIgnore
    public static List<String> getBindableFieldNames() {
        return List.of("collectionDate", "collectors", "fullIdentifier", "authors",
                "type", "category");
    }

    // ----------- Concepts for system fields

    // Specimen identifier
    private static Concept specimenIdConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286193")
            .build();

    // Authors
    @Transient
    @JsonIgnore
    private static Concept authorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286246")
            .build();

    // Excavators
    @Transient
    @JsonIgnore
    private static Concept collectorsConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286247")
            .build();

    // Specimen type
    @Transient
    @JsonIgnore
    private static Concept specimenTypeConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4282392")
            .build();

    // Specimen category
    @Transient
    @JsonIgnore
    private static Concept specimenCategoryConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286248")
            .build();

    // Date
    @Transient
    @JsonIgnore
    private static Concept collectionDateConcept = new Concept.Builder()
            .vocabulary(SYSTEM_THESO)
            .externalId("4286249")
            .build();

    // --------------- Fields

    // Fields
    private static CustomFieldText specimenIdField = CustomFieldText.builder()
            .label("recordingunit.field.identifier")
            .isSystemField(true)
            .id(1L)
            .valueBinding("fullIdentifier")
            .concept(specimenIdConcept)
            .build();


    @Transient
    @JsonIgnore
    private static CustomFieldSelectMultiplePerson authorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.authors")
            .isSystemField(true)
            .id(2L)
            .valueBinding("authors")
            .concept(authorsConcept)
            .build();

    @Transient
    @JsonIgnore
    private static CustomFieldSelectMultiplePerson collectorsField =  CustomFieldSelectMultiplePerson.builder()
            .label("specimen.field.collectors")
            .isSystemField(true)
            .id(3L)
            .valueBinding("collectors")
            .concept(collectorsConcept)
            .build();

    @Transient
    @JsonIgnore
    private static CustomFieldSelectOneFromFieldCode specimenTypeField =  CustomFieldSelectOneFromFieldCode.builder()
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
    private static CustomFieldSelectOneFromFieldCode specimenCategoryField =  CustomFieldSelectOneFromFieldCode.builder()
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
    private static CustomFieldDateTime collectionDateField =  CustomFieldDateTime.builder()
            .label("specimen.field.collectionDate")
            .isSystemField(true)
            .id(6L)
            .valueBinding("collectionDate")
            .showTime(false)
            .concept(collectionDateConcept)
            .build();

    @Transient
    @JsonIgnore
    public static final CustomForm DETAILS_FORM = new CustomForm.Builder()
            .name("Details tab form")
            .description("Contains the main form")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name("common.header.general")
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(specimenIdField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(authorsField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(collectorsField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(specimenTypeField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(specimenCategoryField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(collectionDateField)
                                                    .build())
                                            .build()
                            )
                            .build()
            )
            .build();

    @Transient
    @JsonIgnore
    public static final CustomForm NEW_UNIT_FORM = new CustomForm.Builder()
            .name("Details tab form")
            .description("Contains the main form")
            .addPanel(
                    new CustomFormPanel.Builder()
                            .name("common.header.general")
                            .isSystemPanel(true)
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(authorsField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(collectorsField)
                                                    .build())
                                            .build()
                            )
                            .addRow(
                                    new CustomRow.Builder()
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(specimenCategoryField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(specimenTypeField)
                                                    .build())
                                            .addColumn(new CustomCol.Builder()
                                                    .readOnly(false)
                                                    .isRequired(true)
                                                    .className(COLUMN_CLASS_NAME)
                                                    .field(collectionDateField)
                                                    .build())
                                            .build()
                            ).build()
            )
            .build();

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}