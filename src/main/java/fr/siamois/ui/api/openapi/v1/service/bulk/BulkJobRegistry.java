package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bulk jobs: those running on this instance, live in memory, and every job — running here or
 * elsewhere, or ended — as kept in the database (BulkJobStore). A finished job is forgotten after
 * {@link #RESULT_TTL}.
 */
@Component
@RequiredArgsConstructor
public class BulkJobRegistry {

    /** How long the result of a finished job is kept for its client to read. */
    static final Duration RESULT_TTL = Duration.ofHours(24);

    private final Map<String, BulkJob> running = new ConcurrentHashMap<>();
    private final BulkJobStore store;

    /**
     * Registers a new job; refused (409) while the person has another one running.
     *
     * @param ownerId the person who starts it
     * @param total   how many elements it acts on
     * @return the job, running
     */
    public BulkJob start(long ownerId, int total) {
        Instant now = Instant.now();
        store.purgeFinishedBefore(now.minus(RESULT_TTL));
        BulkJob job = new BulkJob(ownerId, total);
        store.insertRunning(job, now);
        running.put(job.id(), job);
        return job;
    }

    /**
     * The job's current state, if it exists and this person started it: live when it runs here, as kept
     * in the database otherwise.
     *
     * @param jobId   the job's id
     * @param ownerId the person asking
     * @return the job, or empty (unknown, forgotten, or someone else's)
     */
    public Optional<BulkJobResource> find(String jobId, long ownerId) {
        BulkJob live = running.get(jobId);
        if (live != null) {
            return live.ownedBy(ownerId) ? Optional.of(live.toResource()) : Optional.empty();
        }
        return store.find(jobId, ownerId, Instant.now());
    }

    /**
     * Asks to stop a job this person started: it stops before its next element, wherever it runs.
     *
     * @param jobId   the job's id
     * @param ownerId the person asking
     * @return true when the job exists and was still running
     */
    public boolean cancel(String jobId, long ownerId) {
        BulkJob live = running.get(jobId);
        if (live != null && live.ownedBy(ownerId)) live.requestCancel();
        return store.requestCancel(jobId, ownerId) || (live != null && live.ownedBy(ownerId));
    }

    /**
     * Writes a running job's progress, and picks up a stop asked on another instance.
     *
     * @param job the job
     */
    void saveProgress(BulkJob job) {
        if (store.saveProgress(job.toResource(), Instant.now())) job.requestCancel();
    }

    /**
     * Writes how the job ended; it no longer runs here.
     *
     * @param job the ended job
     */
    void finish(BulkJob job) {
        try {
            store.saveEnd(job.toFullResource(), Instant.now());
        } finally {
            running.remove(job.id());
        }
    }
}
