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

    /** Logs completion of one persist batch — called by seeders after each chunked (or single) saveAll. */
    public static void logBatch(String seederName, int itemsDoneSoFar, int chunkSize, int totalItems) {
        int totalBatches = Math.max(1, (int) Math.ceil((double) totalItems / chunkSize));
        int batchNum = Math.max(1, (int) Math.ceil((double) itemsDoneSoFar / chunkSize));
        log.info("[{}] batch {}/{} done ({}/{} rows persisted)", seederName, batchNum, totalBatches, itemsDoneSoFar, totalItems);
    }
}
