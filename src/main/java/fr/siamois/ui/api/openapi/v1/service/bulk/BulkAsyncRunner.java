package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.utils.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Runs a bulk action on a background thread (the bulkTaskExecutor), element by element, recording its
 * progress on the job the client polls — the same split as ImportAsyncRunner: the work itself stays
 * synchronous, only its scheduling is asynchronous.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkAsyncRunner {

    private final BulkFailureTranslator failureTranslator;

    /**
     * Processes every element, then marks the job done (or failed, on an unexpected error).
     *
     * @param job    the job to record progress on
     * @param ids    the elements, in order
     * @param action one element's unit operation, in its own transaction
     */
    @Async("bulkTaskExecutor")
    public void run(BulkJob job, List<Long> ids, LongConsumer action) {
        try {
            ids.forEach(id -> job.attempt(id, action, failureTranslator));
            job.complete(Instant.now());
        } catch (RuntimeException e) {
            log.error("Bulk job {} stopped", job.id(), e);
            job.fail(MessageUtils.describe(e), Instant.now());
        }
    }
}
