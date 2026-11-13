package fr.siamois.domain.models.bulk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A bulk action on a list's selection, as kept in the database while it runs in the background and for a
 * while after: its progress (the counts, written as it goes), its result (the elements, once it has ended)
 * and whether its owner asked to stop it. Lets any instance follow a job, and a job outlive a restart as a
 * result (a RUNNING job silent for too long died with its instance).
 */
@Entity
@Table(name = "bulk_job")
@Getter
@Setter
@NoArgsConstructor
public class BulkJobRecord {

    @Id
    @Column(name = "bulk_job_id", length = 36)
    private String id;

    @Column(name = "fk_person", nullable = false)
    private Long ownerId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "total", nullable = false)
    private int total;

    @Column(name = "succeeded_count", nullable = false)
    private int succeededCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    /** The elements changed, once the job has ended. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "succeeded", columnDefinition = "jsonb")
    private List<Long> succeeded;

    /** The elements refused ({@code id}, {@code status}, {@code message}), once the job has ended. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failed", columnDefinition = "jsonb")
    private List<Map<String, Object>> failed;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
