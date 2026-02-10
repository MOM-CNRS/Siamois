package fr.siamois.utils;

public class TimerUtils {
    public static void withTimer(Runnable task, String taskName) {
        long startTime = System.nanoTime();
        task.run();
        long endTime = System.nanoTime();
        double duration = (endTime - startTime) / 1_000_000.0; // Convert to milliseconds
        System.out.println("Execution time for "+taskName+": " + duration + " ms");
    }
}
