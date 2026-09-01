package fr.siamois.infrastructure.database.initializer.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class SeederUtils {

    private static final Logger log = LoggerFactory.getLogger(SeederUtils.class);

    private SeederUtils() {
        /* This utility class should not be instantiated */
    }


    public static <T> T field(String name, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new IllegalStateException("Champ '" + name + "' : " + e.getMessage(), e);
        }
    }

    /**
     * The line number to report in a "[Xxx ligne N]" error prefix: the real Excel row number when
     * the spec carries one (set during parsing from the actual sheet row), falling back to its
     * 1-based position in the specs list otherwise — e.g. for specs built directly in tests or by
     * the dataset-fixture initializers, which have no original spreadsheet row to point to.
     */
    public static int lineNumber(Integer excelRowNumber, int zeroBasedIndex) {
        return excelRowNumber != null ? excelRowNumber : zeroBasedIndex + 1;
    }

    /** Logs completion of one persist batch — called by seeders after each chunked (or single) saveAll. */
    public static void logBatch(String seederName, int itemsDoneSoFar, int chunkSize, int totalItems) {
        int totalBatches = Math.max(1, (int) Math.ceil((double) totalItems / chunkSize));
        int batchNum = Math.max(1, (int) Math.ceil((double) itemsDoneSoFar / chunkSize));
        log.info("[{}] batch {}/{} done ({}/{} rows persisted)", seederName, batchNum, totalBatches, itemsDoneSoFar, totalItems);
    }
}
