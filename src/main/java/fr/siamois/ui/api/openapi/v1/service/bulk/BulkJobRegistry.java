package fr.siamois.ui.api.openapi.v1.service.bulk;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bulk actions running or recently ended, in memory: a client follows the one it started until it
 * ends, then reads its result. A finished job is forgotten after {@link #RESULT_TTL}.
 */
@Component
public class BulkJobRegistry {

    /** How long the result of a finished job is kept for its client to read. */
    static final Duration RESULT_TTL = Duration.ofHours(1);

    private final Map<String, BulkJob> jobs = new ConcurrentHashMap<>();

    /**
     * Registers a new job.
     *
     * @param ownerId the person who starts it
     * @param total   how many elements it acts on
     * @return the job, running
     */
    public BulkJob start(long ownerId, int total) {
        purgeExpired(Instant.now());
        BulkJob job = new BulkJob(ownerId, total);
        jobs.put(job.id(), job);
        return job;
    }

    /**
     * The job, if it exists and this person started it.
     *
     * @param jobId   the job's id
     * @param ownerId the person asking
     * @return the job, or empty (unknown, forgotten, or someone else's)
     */
    public Optional<BulkJob> find(String jobId, long ownerId) {
        purgeExpired(Instant.now());
        return Optional.ofNullable(jobs.get(jobId)).filter(job -> job.ownedBy(ownerId));
    }

    /**
     * Forgets the jobs that ended more than {@link #RESULT_TTL} ago.
     *
     * @param now the current time
     */
    void purgeExpired(Instant now) {
        jobs.values().removeIf(job -> job.expired(now, RESULT_TTL));
    }
}
