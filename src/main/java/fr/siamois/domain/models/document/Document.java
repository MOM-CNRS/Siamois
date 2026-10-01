package fr.siamois.domain.models.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.util.MimeType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A document: a physical file stored by the server and/or an external URL, described by the national
 * common-base fields and attached to exactly one project ({@link #actionUnit}), then optionally linked
 * to any number of recording units, finds, places, phases and containers.
 * <p>
 * The links are owned by this side (one join table per target type, real foreign keys), so a target
 * entity needs no collection of its own: its "documents" are found from here.
 */
@Data
@Entity
@Table(name = "siamois_document", schema = "public",
        indexes = @Index(name = "idx_document_action_unit", columnList = "fk_action_unit_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_document_project_identifier",
                columnNames = {"fk_action_unit_id", "identifier"}))
@Audited
public class Document extends TraceableEntity implements ArkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id", nullable = false)
    private Long id;

    /** The "category" (arrêté du 7 février 2022): the document table's configurable type. */
    @FieldCode
    public static final String TYPE_FIELD = "SIAD.CATEGORY";

    @FieldCode
    public static final String DOCUMENT_TYPE_FIELD = "SIAD.TYPE";

    @FieldCode
    public static final String NATURE_FIELD_CODE = "SIAD.NATURE";

    @FieldCode
    public static final String SCALE_FIELD_CODE = "SIAD.SCALE";

    @FieldCode
    public static final String FORMAT_FIELD_CODE = "SIAD.FORMAT";

    @FieldCode
    public static final String LANGUAGE_FIELD_CODE = "SIAD.LANGUAGE";

    @FieldCode
    public static final String KEYWORD_FIELD_CODE = "SIAD.KEYWORD";

    public String contentFileName() {
        return fileCode + "." + fileExtension();
    }

    public MimeType mimeTypeObject() {
        return MimeType.valueOf(mimeType);
    }

    public String fileExtension() {
        int i = fileName.lastIndexOf('.');
        return fileName.substring(i + 1);
    }

    /**
     * The nature of the support the mobile API exposes (it carries a single one): the first of the
     * multi-valued {@link #supportNatures}, by concept id so that it is stable.
     */
    public Concept primaryNature() {
        return supportNatures.stream()
                .min(java.util.Comparator.comparing(Concept::getId, java.util.Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
    }

    /** Replaces the support natures by the single one the mobile API sends; {@code null} clears them. */
    public void replaceNatureBy(Concept nature) {
        supportNatures.clear();
        if (nature != null) {
            supportNatures.add(nature);
        }
    }

    /** True when a physical file is stored for this document. */
    public boolean hasFile() {
        return fileCode != null && fileName != null;
    }

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_ark_id")
    protected Ark ark;

    // ---------------------------------------------------------------- Identification

    /** Unique within the project; generated from the project's identifier format, then editable. */
    @Column(name = "identifier", nullable = false)
    protected String identifier;

    /** Raw allocated counter value; kept separately because it cannot be inferred from the format. */
    @Column(name = "generated_number")
    protected Integer generatedNumber;

    @Column(name = "other_identifiers", length = MAX_OTHER_IDENTIFIERS_LENGTH)
    protected String otherIdentifiers;

    /**
     * The attachment project. Mandatory for every document created by the application; documents that
     * predate the field and could not be attached by the migration keep it null until repaired by hand.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_action_unit_id")
    protected ActionUnit actionUnit;

    /** The category (arrêté du 7 février 2022), which also selects the document's form configuration. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_category")
    protected Concept category;

    /** The document type, restricted per category by the category's form configuration. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_document_type")
    protected Concept documentType;

    // ---------------------------------------------------------------- Description

    @Column(name = "title", length = MAX_TITLE_LENGTH)
    protected String title;

    @Column(name = "doc_description", length = MAX_DESCRIPTION_LENGTH)
    protected String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_scale")
    protected Concept scale;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_language")
    protected Concept language;

    @Column(name = "production_date")
    protected OffsetDateTime productionDate;

    @Column(name = "publisher")
    protected String publisher;

    @Column(name = "rights", length = MAX_DESCRIPTION_LENGTH)
    protected String rights;

    @Column(name = "comments", length = MAX_DESCRIPTION_LENGTH)
    protected String comments;

    // ---------------------------------------------------------------- Technical

    /** Free text: the file's format is not restricted to a vocabulary. Filled from the file when empty. */
    @Column(name = "format_text")
    protected String format;

    /**
     * The format as a concept, the way the mobile API has always exposed it. Kept for that contract;
     * the form's {@link #format} is the text field.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_format")
    protected Concept formatConcept;

    @Column(name = "item_count")
    protected Integer itemCount;

    /** Size in megabytes: filled from the file when there is one, typed otherwise (a document may be physical). */
    @Column(name = "size_mb", precision = 14, scale = 3)
    protected BigDecimal sizeMb;

    /** Coordinate reference system the document is expressed in (SCR). */
    @Column(name = "crs")
    protected String crs;

    /** Where the original lives (path or location). */
    @Column(name = "original_path", length = MAX_DESCRIPTION_LENGTH)
    protected String originalPath;

    // ---------------------------------------------------------------- File and URL

    /**
     * The external URL the document can be found at. Not to be confused with {@link #url}, the
     * server's own {@code /content/...} URL of the stored file.
     */
    @Column(name = "external_url", length = MAX_URL_LENGTH)
    protected String externalUrl;

    @Column(name = "url", length = Integer.MAX_VALUE)
    protected String url;

    @Column(name = "file_name")
    protected String fileName;

    @Column(name = "mime_type", length = Integer.MAX_VALUE)
    protected String mimeType;

    protected Long size;

    @Column(name = "file_internal_code", length = FILE_INTERNAL_CODE_LENGTH, unique = true)
    protected String fileCode;

    @Column(name = "md5_sum")
    protected String md5Sum;

    protected String storedFileName;

    // ---------------------------------------------------------------- Multiple values

    @ManyToMany
    @JoinTable(
            name = "document_support_nature",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_concept_id")
    )
    @NotAudited
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Set<Concept> supportNatures = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "document_keyword",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_concept_id")
    )
    @NotAudited
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Set<Concept> keywords = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "document_author",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_person_id")
    )
    @NotAudited
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Set<Person> authors = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "document_contributor",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_person_id")
    )
    @NotAudited
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Set<Person> contributors = new HashSet<>();

    // ---------------------------------------------------------------- Links to other entities
    // Optional and multiple; the project link above is the single mandatory one.

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "recording_unit_document",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_recording_unit_id")
    )
    @NotAudited
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Set<RecordingUnit> recordingUnits = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "specimen_document",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_specimen_id")
    )
    @NotAudited
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Set<Specimen> finds = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "spatial_unit_document",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_spatial_unit_id")
    )
    @NotAudited
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Set<SpatialUnit> places = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "phase_document",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_phase_id")
    )
    @NotAudited
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Set<Phase> phases = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "container_document",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_container_id")
    )
    @NotAudited
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Set<Container> containers = new HashSet<>();

    public static final int MAX_FILE_NAME_LENGTH = 255;
    public static final int FILE_INTERNAL_CODE_LENGTH = 10;
    public static final int MAX_TITLE_LENGTH = 1024;
    public static final int MAX_DESCRIPTION_LENGTH = 5000;
    public static final int MAX_OTHER_IDENTIFIERS_LENGTH = 1024;
    public static final int MAX_URL_LENGTH = 2048;

    /** Two documents are the same row; the content hash says nothing about it (a document may have no file). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Document.class.hashCode();
    }

}
