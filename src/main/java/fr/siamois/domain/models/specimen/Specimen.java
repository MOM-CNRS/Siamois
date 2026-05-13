package fr.siamois.domain.models.specimen;


import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.actionunit.NullActionUnitIdentifierException;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.form.SpecimenDetailsForm;
import fr.siamois.domain.models.specimen.form.SpecimenNewUnitForm;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Data
@Entity
@Table(name = "specimen", indexes = {
        @Index(columnList = "full_identifier", name = "idx_specimen_full_identifier"),
        @Index(columnList = "fk_institution_id", name = "idx_specimen_institution")
})
@Audited
@NoArgsConstructor
public class Specimen extends TraceableEntity implements ArkEntity {

    @SuppressWarnings("CopyConstructorMissesField")
    public Specimen(@NonNull Specimen specimen) {
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

    @FieldCode
    public static final String CLASS_FIELD = "SIAS.CLASS";

    @NotNull
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_specimen_category")
    protected Concept category; // lot, object, echantillon

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_interpretation")
    protected Concept normalizedInterpretation;

    @ManyToMany
    @JoinTable(
            name = "specimen_material",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_material_id"))
    protected Set<Concept> material;

    @ManyToMany(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinTable(
            name="specimen_hierarchy",
            joinColumns = { @JoinColumn(name = "fk_parent_id") },
            inverseJoinColumns = { @JoinColumn(name = "fk_child_id") }
    )
    private Set<Specimen> children = new HashSet<>();

    @ManyToMany(mappedBy = "children", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<Specimen> parents = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "specimen_material_class",
            joinColumns = @JoinColumn(name = "fk_specimen_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_material_class__id"))
    protected Set<Concept> materialClass;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_collection_method")
    protected Concept collectionMethod;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_sanitary_state")
    protected Concept sanitaryState;


    @Column(name = "collection_date")
    protected OffsetDateTime collectionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_recording_unit_id")
    protected RecordingUnit recordingUnit;

    @NotNull
    @Column(name = "identifier")
    protected Integer identifier;

    @NotNull
    @Column(name = "isolat_identifier")
    protected String isolationNumber;

    @Column(name = "taq")
    protected Integer taq;

    @Column(name = "tpq")
    protected Integer tpq;

    @NotNull
    @Column(name = "other_identifier")
    protected String otherIdentifier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_chronological_attribution")
    protected Concept chronologicalAttribution;

    @Column(name = "number_of_element")
    protected Integer numberOfElements;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fk_weight")
    @Audited(targetAuditMode = NOT_AUDITED)
    private MeasurementAnswer weight;

    @NotNull
    @Column(name = "full_identifier")
    protected String fullIdentifier;

    @Column(name = "description", length = 5000)
    protected String description;

    @Column(name = "comments", length = 5000)
    protected String comments;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Specimen that = (Specimen) o;
        return Objects.equals(fullIdentifier, that.fullIdentifier);  // Compare based on RecordingUnit
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullIdentifier);  // Hash based on RecordingUnit
    }

    // utils
    public String displayFullIdentifier() {
        if(getFullIdentifier() == null) {
            if(getRecordingUnit().getFullIdentifier() == null) {
                throw new NullActionUnitIdentifierException("Recording identifier must be set");
            }
            return getRecordingUnit().getFullIdentifier() + "-" + (getIdentifier() == null ? "?" : getIdentifier());
        }
        else {
            return getFullIdentifier();
        }
    }

    @Transient
    @JsonIgnore
    public static final CustomForm DETAILS_FORM = SpecimenDetailsForm.build();

    @Transient
    @JsonIgnore
    public static final CustomForm NEW_UNIT_FORM = SpecimenNewUnitForm.build();


}