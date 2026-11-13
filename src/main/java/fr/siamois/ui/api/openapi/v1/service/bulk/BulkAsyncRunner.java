package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.utils.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Runs a bulk action on a background thread (the bulkTaskExecutor), element by element, recording its
 * progress on the job the client polls — the same split as ImportAsyncRunner: the work itself stays
 * synchronous, only its scheduling is asynchronous. The progress is written to the database every
 * {@link #PROGRESS_EVERY} (which also picks up a stop asked on another instance), the result at the end.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkAsyncRunner {

    /** How often a running job writes its progress to the database. */
    static final Duration PROGRESS_EVERY = Duration.ofSeconds(1);

    private final BulkFailureTranslator failureTranslator;
    private final BulkJobRegistry registry;

    /**
     * Processes the elements until the end or a stop, then records how the job ended (done, cancelled,
     * or failed on an unexpected error).
     *
     * @param job    the job to record progress on
     * @param ids    the elements, in order
     * @param action one element's unit operation, in its own transaction
     */
    @Async("bulkTaskExecutor")
    public void run(BulkJob job, List<Long> ids, LongConsumer action) {
        try {
            process(job, ids, action);
        } catch (RuntimeException e) {
            log.error("Bulk job {} stopped", job.id(), e);
            job.fail(MessageUtils.describe(e));
        } finally {
            registry.finish(job);
        }
    }

    /**
     * The elements one after the other, the progress written now and then, a stop honoured before each.
     */
    private void process(BulkJob job, List<Long> ids, LongConsumer action) {
        Instant nextProgress = Instant.now().plus(PROGRESS_EVERY);
        for (Long id : ids) {
            if (job.cancelRequested()) {
                job.cancel();
                return;
            }
            job.attempt(id, action, failureTranslator);
            if (Instant.now().isAfter(nextProgress)) {
                registry.saveProgress(job);
                nextProgress = Instant.now().plus(PROGRESS_EVERY);
            }
        }
        job.complete();
    }
}
