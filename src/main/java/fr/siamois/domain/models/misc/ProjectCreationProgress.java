package fr.siamois.domain.models.misc;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe progress of a project creation: written by the request thread that creates the project (the
 * template appliers plan their steps and tick them off) and read by the requests polling for it (the JSF
 * progress bar of the settings, the REST progress endpoint of the React app).
 * Deliberately separate from {@link ProgressWrapper}, a plain POJO that is not safe to read from another thread.
 */
public class ProjectCreationProgress implements Serializable {

    private static final int FULL = 100;

    private final AtomicInteger done = new AtomicInteger(0);
    private final AtomicInteger planned = new AtomicInteger(0);

    /**
     * A progress nobody reads, for the creations that are not followed (duplication, seeding, tests).
     *
     * @return a new progress
     */
    public static ProjectCreationProgress unwatched() {
        return new ProjectCreationProgress();
    }

    /**
     * Plans the steps of the creation and starts counting them from zero.
     *
     * @param steps the number of steps the creation is made of
     */
    public void start(int steps) {
        done.set(0);
        planned.set(Math.max(steps, 0));
    }

    /**
     * Ticks one step off.
     */
    public void advance() {
        advance(1);
    }

    /**
     * Ticks several steps off at once, e.g. the steps of a part of the creation that is skipped.
     *
     * @param steps the number of steps done
     */
    public void advance(int steps) {
        done.addAndGet(Math.max(steps, 0));
    }

    /**
     * Marks the creation as over, whatever was planned: the progress reads 100 %.
     */
    public void complete() {
        planned.compareAndSet(0, 1);
        done.set(planned.get());
    }

    /**
     * Forgets the creation, so the next one starts from zero.
     */
    public void reset() {
        done.set(0);
        planned.set(0);
    }

    /**
     * How far the creation is.
     *
     * @return a percentage between 0 (nothing planned or done yet) and 100
     */
    public int getPercentage() {
        int total = planned.get();
        if (total == 0) {
            return 0;
        }
        return Math.min(FULL, done.get() * FULL / total);
    }
}
