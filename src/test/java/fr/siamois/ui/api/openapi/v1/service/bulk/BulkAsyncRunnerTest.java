package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BulkAsyncRunnerTest {

    private final BulkAsyncRunner runner = new BulkAsyncRunner(new BulkFailureTranslator());

    /**
     * Every element is processed, its failure recorded without stopping the others, then the job is done.
     */
    @Test
    void processesEveryElementThenEndsTheJob() {
        BulkJob job = new BulkJob(7L, 3);

        runner.run(job, List.of(1L, 2L, 3L), id -> {
            if (id == 2L) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        });

        BulkJobResource result = job.toResource();
        assertThat(result.status()).isEqualTo(BulkJobStatus.DONE);
        assertThat(result.processed()).isEqualTo(3);
        assertThat(result.succeeded()).containsExactly(1L, 3L);
        assertThat(result.failed()).singleElement().extracting(BulkFailureResource::status).isEqualTo(403);
    }

    /**
     * An error outside any element (the translator itself failing) stops the job as failed.
     */
    @Test
    void anUnexpectedErrorFailsTheJob() {
        BulkFailureTranslator broken = new BulkFailureTranslator() {
            @Override
            public BulkFailureResource translate(long id, RuntimeException error) {
                throw new IllegalStateException("translator down");
            }
        };
        BulkJob job = new BulkJob(7L, 1);

        new BulkAsyncRunner(broken).run(job, List.of(1L), id -> {
            throw new IllegalArgumentException("boom");
        });

        assertThat(job.toResource().status()).isEqualTo(BulkJobStatus.FAILED);
        assertThat(job.toResource().error()).isNotBlank();
    }

    /**
     * While a job runs, only its counts are shared; its lists come once it has ended.
     */
    @Test
    void sharesTheListsOnlyOnceTheJobHasEnded() {
        BulkJob job = new BulkJob(7L, 2);
        job.attempt(1L, id -> { }, new BulkFailureTranslator());

        BulkJobResource running = job.toResource();
        assertThat(running.succeededCount()).isEqualTo(1);
        assertThat(running.succeeded()).isEmpty();

        job.complete(java.time.Instant.now());
        assertThat(job.toResource().succeeded()).containsExactly(1L);
    }
}
