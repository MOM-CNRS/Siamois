package fr.siamois.domain.models.misc;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe progress tracker for the async import parse/persist phases — written from a background
 * thread ({@link fr.siamois.domain.services.dataimport.ImportAsyncRunner}) and read from JSF poll requests.
 * Deliberately separate from {@link ProgressWrapper}, which is a plain (non-thread-safe) POJO used
 * synchronously elsewhere.
 */
public class ImportProgress implements Serializable {

    public enum Phase { IDLE, OPENING, PARSING, PERSISTING, DONE, ERROR }

    private volatile Phase phase = Phase.IDLE;
    private final AtomicInteger current = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);
    private volatile String errorMessage;

    public void start(Phase p, int totalCount) {
        phase = p;
        current.set(0);
        total.set(totalCount);
        errorMessage = null;
    }

    public void increment() {
        current.incrementAndGet();
    }

    public void advance(int rows) {
        current.addAndGet(rows);
    }

    public void complete() {
        phase = Phase.DONE;
    }

    public void fail(String message) {
        phase = Phase.ERROR;
        errorMessage = message;
    }

    public int getPercentage() {
        int t = total.get();
        return t == 0 ? 0 : Math.min(100, current.get() * 100 / t);
    }

    public Phase getPhase() {
        return phase;
    }

    public int getCurrent() {
        return current.get();
    }

    public int getTotal() {
        return total.get();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRunning() {
        return phase == Phase.OPENING || phase == Phase.PARSING || phase == Phase.PERSISTING;
    }

    public boolean isOpening() {
        return phase == Phase.OPENING;
    }

    public boolean isParsing() {
        return phase == Phase.PARSING;
    }

    public boolean isFinished() {
        return phase == Phase.DONE || phase == Phase.ERROR;
    }

    /** Called once the poll handler has consumed a finished phase, so the progress panel disappears. */
    public void reset() {
        phase = Phase.IDLE;
        current.set(0);
        total.set(0);
        errorMessage = null;
    }
}
