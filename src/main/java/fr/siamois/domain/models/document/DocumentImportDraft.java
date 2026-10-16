package fr.siamois.domain.models.document;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;


@Data
@Entity
@Table(name = "document_import_draft",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_import_draft_person_institution", columnNames = {"fk_person_id", "fk_institution_id"}))
public class DocumentImportDraft implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "draft_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_person_id", nullable = false)
    private Person person;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_institution_id", nullable = false)
    private Institution institution;

    /** REST segment used when the dialog was last opened from a fiche (projects, recording-units…). */
    @Column(name = "default_target_type", length = 64)
    private String defaultTargetType;

    @Column(name = "default_target_id")
    private Long defaultTargetId;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DocumentImportItem> items = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = updatedAt;
        }
    }
}