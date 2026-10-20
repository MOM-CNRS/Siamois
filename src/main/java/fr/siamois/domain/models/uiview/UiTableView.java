package fr.siamois.domain.models.uiview;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.dto.view.TableViewState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "ui_table_view")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UiTableView {

    @Id
    @GeneratedValue
    private Long id;

    /**
     * The resource this view applies to: the key of the entity type in the front registry,
     * e.g. "recordingUnit", "project".
     */
    private String resourceType;

    private String title;

    /**
     * The owner of the view; null for a {@link UiViewKind#DEFAULT} view, which belongs to nobody.
     */
    @ManyToOne
    @JoinColumn(name = "fk_owner")
    private Person owner;

    /**
     * The organisation the view lives in: every member of it sees the view. Nullable in the schema
     * only because views created before this column existed have none, and are not listed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_institution")
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'USER'")
    @Builder.Default
    private UiViewKind kind = UiViewKind.USER;

    /**
     * For a {@link UiViewKind#DEFAULT} view: the project it is the default of, or null for a default
     * that is not project-bound (the project list itself).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_project")
    private ActionUnit project;

    /**
     * For a {@link UiViewKind#DEFAULT} view: the record type (e.g. a recording unit type) it is the
     * default of, or null when the default is for every type.
     */
    private Long typeId;

    /**
     * When the view lists the records related to a parent record (the recording units of a project),
     * the registry key of that parent. This is what lets a view later be a tab of a record's page.
     */
    private String scopeEntityType;

    /**
     * The actual UI configuration (columns, filters, sorting, etc.)
     * Stored as JSONB in PostgreSQL
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private TableViewState state;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
