package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongConsumer;

/**
 * A bulk action running in the background: written by its worker thread, element by element, and read
 * by the requests that poll its progress — every access is synchronized.
 */
public final class BulkJob {

    private final String id = UUID.randomUUID().toString();
    private final long ownerId;
    private final int total;
    private final List<Long> succeeded = new ArrayList<>();
    private final List<BulkFailureResource> failed = new ArrayList<>();
    private BulkJobStatus status = BulkJobStatus.RUNNING;
    private String error;
    private Instant finishedAt;

    /**
     * A job of {@code total} elements, started by the person {@code ownerId}.
     *
     * @param ownerId the person who started it: the only one who may follow it
     * @param total   how many elements it acts on
     */
    BulkJob(long ownerId, int total) {
        this.ownerId = ownerId;
        this.total = total;
    }

    /**
     * The job's id.
     *
     * @return the id clients poll
     */
    public String id() {
        return id;
    }

    /**
     * Runs the action on one element and records its success, or its failure.
     *
     * @param elementId  the element's id
     * @param action     the element's unit operation
     * @param translator how a failure is reported
     */
    void attempt(long elementId, LongConsumer action, BulkFailureTranslator translator) {
        try {
            action.accept(elementId);
            recordSuccess(elementId);
        } catch (RuntimeException e) {
            recordFailure(translator.translate(elementId, e));
        }
    }

    /**
     * Records an element changed.
     */
    private synchronized void recordSuccess(long elementId) {
        succeeded.add(elementId);
    }

    /**
     * Records an element that could not be changed.
     */
    private synchronized void recordFailure(BulkFailureResource failure) {
        failed.add(failure);
    }

    /**
     * Every element was processed.
     *
     * @param now when it ended
     */
    synchronized void complete(Instant now) {
        status = BulkJobStatus.DONE;
        finishedAt = now;
    }

    /**
     * The job stopped before the end.
     *
     * @param reason why
     * @param now    when it stopped
     */
    synchronized void fail(String reason, Instant now) {
        status = BulkJobStatus.FAILED;
        error = reason;
        finishedAt = now;
    }

    /**
     * Whether this person started the job.
     *
     * @param personId the person asking
     * @return true for the job's owner
     */
    boolean ownedBy(long personId) {
        return ownerId == personId;
    }

    /**
     * Whether the job ended more than {@code ttl} ago: its result is no longer kept.
     *
     * @param now the current time
     * @param ttl how long a result is kept
     * @return true once the result may be forgotten
     */
    synchronized boolean expired(Instant now, Duration ttl) {
        return finishedAt != null && finishedAt.plus(ttl).isBefore(now);
    }

    /**
     * The job as clients see it: a consistent snapshot of its progress.
     *
     * @return the snapshot
     */
    synchronized BulkJobResource toResource() {
        return new BulkJobResource(id, status, total, succeeded.size() + failed.size(),
                List.copyOf(succeeded), List.copyOf(failed), error);
    }
}
