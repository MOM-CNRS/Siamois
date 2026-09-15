package fr.siamois.utils;

import fr.siamois.ui.config.CurrentPhaseHolder;
import jakarta.faces.event.PhaseId;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs each SQL statement executed through {@link fr.siamois.infrastructure.config.TimingDataSourceProxy},
 * tagged with its position in the current request's query sequence, the JSF lifecycle phase that was
 * running when it fired (from {@link CurrentPhaseHolder}), and how long it took — so a request's log can
 * be read as a timeline of "which phase triggered how many queries, and how much of the request's time
 * they actually cost" instead of an undated bag of SQL statements.
 */
@Slf4j
public final class SqlTimingStats {

    private SqlTimingStats() {
    }

    public static void record(String sql, long elapsedNanos) {
        if (!log.isDebugEnabled()) {
            return;
        }
        PhaseId phase = CurrentPhaseHolder.get();
        int seq = CurrentPhaseHolder.nextSqlSequence();
        log.debug("🗄 SQL #{} [phase={}] {} ms : {}",
                seq, phase != null ? phase : "OUTSIDE_JSF_LIFECYCLE",
                elapsedNanos / 1_000_000.0, oneLine(sql));
    }

    private static String oneLine(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
    }
}
