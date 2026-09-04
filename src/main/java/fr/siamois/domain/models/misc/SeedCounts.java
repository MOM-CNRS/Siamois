package fr.siamois.domain.models.misc;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-table created/updated/skipped-duplicate row counts collected during the import persist phase,
 * so the post-import summary can show what actually happened in the DB rather than just the number of
 * rows parsed from the file. Written on the background persist thread, read afterward from a JSF poll
 * request — safe without further synchronization since every write happens before the
 * {@link ImportProgress#complete()} volatile write that the polling thread synchronizes on (see the
 * happens-before note in {@code ImportAsyncRunner}).
 */
public class SeedCounts implements Serializable {

    public record Counts(int created, int updated, int skippedDuplicate) {
    }

    private static final Counts ZERO = new Counts(0, 0, 0);

    private final Map<String, Counts> byTable = new HashMap<>();

    public void record(String tableId, int created, int updated, int skippedDuplicate) {
        byTable.put(tableId, new Counts(created, updated, skippedDuplicate));
    }

    public Counts get(String tableId) {
        return byTable.getOrDefault(tableId, ZERO);
    }

    public void reset() {
        byTable.clear();
    }
}
