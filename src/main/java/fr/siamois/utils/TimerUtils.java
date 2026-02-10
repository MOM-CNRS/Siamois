package fr.siamois.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class TimerUtils {

    private TimerUtils() {
        /* This utility class should not be instantiated */
    }

    public static void withTimer(Runnable task, String taskName) {
        long startTime = System.nanoTime();
        task.run();
        long endTime = System.nanoTime();
        double duration = (endTime - startTime) / 1_000_000.0; // Convert to milliseconds
        log.info("Execution time for "+taskName+": " + duration + " ms");
    }
}
