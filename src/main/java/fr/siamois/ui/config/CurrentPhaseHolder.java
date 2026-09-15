package fr.siamois.ui.config;

import jakarta.faces.event.PhaseId;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exposes the JSF lifecycle phase the current request is in, and a per-request SQL sequence counter,
 * so that SQL logged by {@link fr.siamois.infrastructure.config.TimingDataSourceProxy} can be tagged
 * with "which phase fired this query, and in what order" — set by {@link TimingPhaseListener}.
 */
public final class CurrentPhaseHolder {

    private CurrentPhaseHolder() {
    }

    private static final ThreadLocal<PhaseId> PHASE = new ThreadLocal<>();
    private static final ThreadLocal<AtomicInteger> SQL_SEQUENCE = ThreadLocal.withInitial(AtomicInteger::new);

    public static void set(PhaseId phaseId) {
        PHASE.set(phaseId);
    }

    public static PhaseId get() {
        return PHASE.get();
    }

    public static int nextSqlSequence() {
        return SQL_SEQUENCE.get().incrementAndGet();
    }

    public static void clear() {
        PHASE.remove();
        SQL_SEQUENCE.remove();
    }
}
