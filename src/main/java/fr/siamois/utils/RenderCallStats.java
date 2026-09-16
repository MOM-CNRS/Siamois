package fr.siamois.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Temporary diagnostic: counts calls and cumulative time of a handful of suspect hot-path methods
 * (label/message resolution, invoked once per field or more by JSF's multi-pass rendering) for the
 * current request, since JFR's CPU sampling ({@code jdk.ExecutionSample}) produces zero samples on
 * this machine (macOS signal-based stack walking silently failing) and per-call debug logging alone
 * would flood the log without telling call counts apart from per-call cost.
 * <p>
 * {@link #record} is meant to wrap a suspect method body; {@link #dumpAndClear()} is called once by
 * {@link fr.siamois.ui.config.TimingPhaseListener} right after RENDER_RESPONSE, so one line per
 * instrumented method summarizes the whole request instead of one line per call.
 */
@Slf4j
public final class RenderCallStats {

    private RenderCallStats() {
    }

    private record Stat(long count, long totalNanos) {
        Stat plus(long nanos) {
            return new Stat(count + 1, totalNanos + nanos);
        }
    }

    private static final ThreadLocal<Map<String, Stat>> STATS = ThreadLocal.withInitial(LinkedHashMap::new);

    public static void record(String label, long nanos) {
        STATS.get().merge(label, new Stat(1, nanos), (oldVal, added) ->
                new Stat(oldVal.count() + added.count(), oldVal.totalNanos() + added.totalNanos()));
    }

    /**
     * Runs {@code call}, records its elapsed time under {@code label}, and returns its result.
     */
    public static <T> T time(String label, java.util.function.Supplier<T> call) {
        long start = System.nanoTime();
        try {
            return call.get();
        } finally {
            record(label, System.nanoTime() - start);
        }
    }

    public static void dumpAndClear() {
        Map<String, Stat> stats = STATS.get();
        if (!stats.isEmpty()) {
            stats.forEach((label, stat) -> log.debug(
                    "⏱ RenderCallStats [{}] : {} appel(s), {} ms cumulés",
                    label, stat.count(), stat.totalNanos() / 1_000_000.0));
        }
        STATS.remove();
    }
}
