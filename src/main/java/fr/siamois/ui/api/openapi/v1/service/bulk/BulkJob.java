package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongConsumer;

/**
 * A bulk action running in the background on this instance: written by its worker thread, element by
 * element, and read by the requests that poll its progress — every access is synchronized. Its progress
 * and result are also kept in the database (BulkJobStore) for the other instances and after a restart.
 */
public final class BulkJob {

    private final String id = UUID.randomUUID().toString();
    private final long ownerId;
    private final int total;
    private final List<Long> succeeded = new ArrayList<>();
    private final List<BulkFailureResource> failed = new ArrayList<>();
    private BulkJobStatus status = BulkJobStatus.RUNNING;
    private String error;
    private boolean cancelRequested;

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
     * The person who started the job.
     *
     * @return their id
     */
    long ownerId() {
        return ownerId;
    }

    /**
     * How many elements the job acts on.
     *
     * @return its total
     */
    int total() {
        return total;
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
     * The owner asked to stop the job: it stops before its next element.
     */
    synchronized void requestCancel() {
        cancelRequested = true;
    }

    /**
     * Whether the job must stop before its next element.
     *
     * @return true once a stop was asked
     */
    synchronized boolean cancelRequested() {
        return cancelRequested;
    }

    /**
     * Every element was processed.
     */
    synchronized void complete() {
        status = BulkJobStatus.DONE;
    }

    /**
     * The job stopped, as its owner asked, before the end.
     */
    synchronized void cancel() {
        status = BulkJobStatus.CANCELLED;
    }

    /**
     * The job stopped before the end, on an unexpected error.
     *
     * @param reason why
     */
    synchronized void fail(String reason) {
        status = BulkJobStatus.FAILED;
        error = reason;
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
     * Whether the job is still running.
     *
     * @return true until it has ended
     */
    synchronized boolean running() {
        return !status.ended();
    }

    /**
     * The job as clients see it: a consistent snapshot of its progress. While it runs, only the counts —
     * a client polls it often, and the lists can hold thousands of elements; once it has ended, the lists.
     *
     * @return the snapshot
     */
    synchronized BulkJobResource toResource() {
        return snapshot(status.ended());
    }

    /**
     * A snapshot with the lists whatever the status: what is written to the database once it has ended.
     *
     * @return the full snapshot
     */
    synchronized BulkJobResource toFullResource() {
        return snapshot(true);
    }

    /**
     * The job's counts and, when asked, its lists (the caller holds the lock).
     */
    private BulkJobResource snapshot(boolean withLists) {
        return new BulkJobResource(id, status, total, succeeded.size() + failed.size(),
                succeeded.size(), failed.size(),
                withLists ? List.copyOf(succeeded) : List.of(),
                withLists ? List.copyOf(failed) : List.of(),
                error);
    }
}
