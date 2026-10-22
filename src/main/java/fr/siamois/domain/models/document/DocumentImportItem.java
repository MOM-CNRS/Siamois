package fr.siamois.domain.models.document;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

/**
 * One uploaded file inside a {@link DocumentImportDraft}, with editable metadata and targets
 * until it is promoted to a real {@link Document}.
 */
@Data
@Entity
@Table(name = "document_import_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_import_item_client_key",
                columnNames = {"fk_draft_id", "client_key"}))
public class DocumentImportItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_draft_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DocumentImportDraft draft;

    @NotNull
    @Column(name = "client_key", nullable = false, length = 64)
    private String clientKey;

    @NotNull
    @Column(name = "original_file_name", nullable = false, length = 1024)
    private String originalFileName;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "md5_sum", length = 64)
    private String md5Sum;

    /** File name only (under the draft directory on disk). */
    @NotNull
    @Column(name = "stored_file_name", nullable = false, length = 255)
    private String storedFileName;

    @Column(name = "title", length = 1024)
    private String title;

    @Column(name = "description", length = 5000)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_nature_concept_id")
    private Concept nature;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_format_concept_id")
    private Concept formatConcept;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_scale_concept_id")
    private Concept scale;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DocumentImportItemStatus status = DocumentImportItemStatus.DRAFT;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "document_import_item_project",
            joinColumns = @JoinColumn(name = "fk_item_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_action_unit_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<ActionUnit> projects = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "document_import_item_recording_unit",
            joinColumns = @JoinColumn(name = "fk_item_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_recording_unit_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<RecordingUnit> recordingUnits = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "document_import_item_specimen",
            joinColumns = @JoinColumn(name = "fk_item_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_specimen_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Specimen> finds = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "document_import_item_spatial_unit",
            joinColumns = @JoinColumn(name = "fk_item_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_spatial_unit_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<SpatialUnit> places = new HashSet<>();

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = updatedAt;
        }
    }
}
