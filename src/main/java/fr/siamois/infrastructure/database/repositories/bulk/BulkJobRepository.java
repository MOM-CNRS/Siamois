package fr.siamois.infrastructure.database.repositories.bulk;

import fr.siamois.domain.models.bulk.BulkJobRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The bulk jobs kept in the database.
 */
public interface BulkJobRepository extends JpaRepository<BulkJobRecord, String> {

    /**
     * A job of this person.
     *
     * @param id      the job's id
     * @param ownerId the person who started it
     * @return the job, or empty when unknown or someone else's
     */
    Optional<BulkJobRecord> findByIdAndOwnerId(String id, Long ownerId);

    /**
     * The jobs of this person in this status.
     *
     * @param ownerId the person
     * @param status  the status
     * @return their jobs
     */
    List<BulkJobRecord> findByOwnerIdAndStatus(Long ownerId, String status);

    /**
     * Whether the owner asked to stop the job.
     *
     * @param id the job's id
     * @return true once a stop was asked
     */
    @Query("select r.cancelRequested from BulkJobRecord r where r.id = :id")
    Optional<Boolean> isCancelRequested(@Param("id") String id);

    /**
     * Forgets the jobs that ended before {@code before}.
     *
     * @param before the oldest end kept
     * @return how many were forgotten
     */
    @Modifying
    @Query("delete from BulkJobRecord r where r.finishedAt is not null and r.finishedAt < :before")
    int deleteFinishedBefore(@Param("before") Instant before);
}
