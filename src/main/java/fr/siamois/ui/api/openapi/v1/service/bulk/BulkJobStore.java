package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.domain.models.bulk.BulkJobRecord;
import fr.siamois.infrastructure.database.repositories.bulk.BulkJobRepository;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The bulk jobs as kept in the database: what lets any instance follow a job, a stop asked on one instance
 * reach the one running it, a result outlive a restart, and at most one job run per person at a time.
 */
@Component
@RequiredArgsConstructor
public class BulkJobStore {

    /** A running job that wrote no progress for this long died with its instance. */
    static final Duration STALE_AFTER = Duration.ofMinutes(5);

    /** Why a job silent for too long is reported as failed. */
    static final String INTERRUPTED = "Action interrompue (redémarrage du serveur)";

    private static final String ALREADY_RUNNING = "Une action en lot est déjà en cours : attendez qu'elle se termine ou arrêtez-la";

    private final BulkJobRepository repository;

    /**
     * Records a job that starts; refuses it (409) while the person has another one running.
     *
     * @param job the job
     * @param now when it starts
     */
    @Transactional
    public void insertRunning(BulkJob job, Instant now) {
        failStaleRunning(job.ownerId(), now);
        if (!repository.findByOwnerIdAndStatus(job.ownerId(), BulkJobStatus.RUNNING.name()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_RUNNING);
        }
        BulkJobRecord bulkJobRecord = new BulkJobRecord();
        bulkJobRecord.setId(job.id());
        bulkJobRecord.setOwnerId(job.ownerId());
        bulkJobRecord.setStatus(BulkJobStatus.RUNNING.name());
        bulkJobRecord.setTotal(job.total());
        bulkJobRecord.setCreatedAt(now);
        bulkJobRecord.setUpdatedAt(now);
        try {
            repository.saveAndFlush(bulkJobRecord);
        } catch (DataIntegrityViolationException e) {
            // Two starts racing past the check above: the partial unique index keeps one.
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_RUNNING, e);
        }
    }

    /**
     * Writes a running job's progress, and tells whether its owner asked to stop it (maybe on another instance).
     *
     * @param snapshot the job's progress
     * @param now      when it is written
     * @return true once a stop was asked
     */
    @Transactional
    public boolean saveProgress(BulkJobResource snapshot, Instant now) {
        return repository.findById(snapshot.id()).map(bulkJobRecord -> {
            bulkJobRecord.setSucceededCount(snapshot.succeededCount());
            bulkJobRecord.setFailedCount(snapshot.failedCount());
            bulkJobRecord.setUpdatedAt(now);
            return bulkJobRecord.isCancelRequested();
        }).orElse(false);
    }

    /**
     * Writes how a job ended: its status, the elements it changed and those it could not.
     *
     * @param snapshot the job's full snapshot
     * @param now      when it ended
     */
    @Transactional
    public void saveEnd(BulkJobResource snapshot, Instant now) {
        repository.findById(snapshot.id()).ifPresent(bulkJobRecord -> {
            bulkJobRecord.setStatus(snapshot.status().name());
            bulkJobRecord.setSucceededCount(snapshot.succeededCount());
            bulkJobRecord.setFailedCount(snapshot.failedCount());
            bulkJobRecord.setSucceeded(List.copyOf(snapshot.succeeded()));
            bulkJobRecord.setFailed(snapshot.failed().stream().map(BulkJobStore::failureToJson).toList());
            bulkJobRecord.setError(snapshot.error());
            bulkJobRecord.setUpdatedAt(now);
            bulkJobRecord.setFinishedAt(now);
        });
    }

    /**
     * A job of this person as the database knows it; one silent for too long is reported as failed.
     *
     * @param jobId   the job's id
     * @param ownerId the person asking
     * @param now     the current time
     * @return the job, or empty when unknown, forgotten or someone else's
     */
    @Transactional
    public Optional<BulkJobResource> find(String jobId, long ownerId, Instant now) {
        return repository.findByIdAndOwnerId(jobId, ownerId).map(bulkJobRecord -> {
            if (isStale(bulkJobRecord, now)) markInterrupted(bulkJobRecord, now);
            return toResource(bulkJobRecord);
        });
    }

    /**
     * Asks to stop a job of this person.
     *
     * @param jobId   the job's id
     * @param ownerId the person asking
     * @return true when the job exists and was still running
     */
    @Transactional
    public boolean requestCancel(String jobId, long ownerId) {
        return repository.findByIdAndOwnerId(jobId, ownerId)
                .filter(bulkJobRecord -> BulkJobStatus.RUNNING.name().equals(bulkJobRecord.getStatus()))
                .map(bulkJobRecord -> {
                    bulkJobRecord.setCancelRequested(true);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Forgets the jobs that ended before {@code before}.
     *
     * @param before the oldest end kept
     */
    @Transactional
    public void purgeFinishedBefore(Instant before) {
        repository.deleteFinishedBefore(before);
    }

    /**
     * Marks as failed the running jobs of this person that died with their instance: they no longer
     * hold the person's one running slot.
     */
    private void failStaleRunning(long ownerId, Instant now) {
        repository.findByOwnerIdAndStatus(ownerId, BulkJobStatus.RUNNING.name()).stream()
                .filter(bulkJobRecord -> isStale(bulkJobRecord, now))
                .forEach(bulkJobRecord -> markInterrupted(bulkJobRecord, now));
        repository.flush();
    }

    /**
     * Whether a running job wrote no progress for too long.
     */
    private static boolean isStale(BulkJobRecord bulkJobRecord, Instant now) {
        return BulkJobStatus.RUNNING.name().equals(bulkJobRecord.getStatus())
                && bulkJobRecord.getUpdatedAt().plus(STALE_AFTER).isBefore(now);
    }

    /**
     * A running job that died with its instance: failed, its progress kept.
     */
    private static void markInterrupted(BulkJobRecord bulkJobRecord, Instant now) {
        bulkJobRecord.setStatus(BulkJobStatus.FAILED.name());
        bulkJobRecord.setError(INTERRUPTED);
        bulkJobRecord.setFinishedAt(now);
        bulkJobRecord.setUpdatedAt(now);
    }

    /**
     * The job as clients see it: the lists only once it has ended.
     */
    private static BulkJobResource toResource(BulkJobRecord bulkJobRecord) {
        BulkJobStatus status = BulkJobStatus.valueOf(bulkJobRecord.getStatus());
        boolean ended = status.ended();
        List<Long> succeeded = ended && bulkJobRecord.getSucceeded() != null ? bulkJobRecord.getSucceeded().stream().map(Number::longValue).toList() : List.of();
        List<BulkFailureResource> failed = ended && bulkJobRecord.getFailed() != null
                ? bulkJobRecord.getFailed().stream().map(BulkJobStore::failureFromJson).toList()
                : List.of();
        return new BulkJobResource(bulkJobRecord.getId(), status, bulkJobRecord.getTotal(),
                bulkJobRecord.getSucceededCount() + bulkJobRecord.getFailedCount(), bulkJobRecord.getSucceededCount(), bulkJobRecord.getFailedCount(),
                succeeded, failed, bulkJobRecord.getError());
    }

    /**
     * A failure as kept in the jsonb column.
     */
    private static Map<String, Object> failureToJson(BulkFailureResource failure) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", failure.id());
        json.put("status", failure.status());
        json.put("message", failure.message());
        return json;
    }

    /**
     * A failure read back from the jsonb column.
     */
    private static BulkFailureResource failureFromJson(Map<String, Object> json) {
        return new BulkFailureResource(((Number) json.get("id")).longValue(), ((Number) json.get("status")).intValue(),
                String.valueOf(json.get("message")));
    }
}
