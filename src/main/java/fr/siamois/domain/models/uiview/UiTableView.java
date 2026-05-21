package fr.siamois.domain.models.uiview;

import fr.siamois.dto.view.TableViewState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
     * The resource this view applies to
     * e.g. "recording-unit", "specimen", etc.
     */
    @Column(nullable = false)
    private String resourceType;

    /**
     * The actual UI configuration (columns, filters, sorting, etc.)
     * Stored as JSONB in PostgreSQL
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private TableViewState state;
}
