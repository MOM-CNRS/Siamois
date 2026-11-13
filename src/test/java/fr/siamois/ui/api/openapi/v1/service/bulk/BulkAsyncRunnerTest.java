package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BulkAsyncRunnerTest {

    @Mock
    private BulkJobRegistry registry;

    /**
     * Every element is processed, its failure recorded without stopping the others, then the job is
     * done and written.
     */
    @Test
    void processesEveryElementThenEndsTheJob() {
        BulkJob job = new BulkJob(7L, 3);

        new BulkAsyncRunner(new BulkFailureTranslator(), registry).run(job, List.of(1L, 2L, 3L), id -> {
            if (id == 2L) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        });

        BulkJobResource result = job.toResource();
        assertThat(result.status()).isEqualTo(BulkJobStatus.DONE);
        assertThat(result.processed()).isEqualTo(3);
        assertThat(result.succeeded()).containsExactly(1L, 3L);
        assertThat(result.failed()).singleElement().extracting(BulkFailureResource::status).isEqualTo(403);
        verify(registry).finish(job);
    }

    /**
     * A stop asked while the job runs stops it before its next element; what was done stays.
     */
    @Test
    void stopsBeforeTheNextElementOnceAStopIsAsked() {
        BulkJob job = new BulkJob(7L, 3);
        List<Long> processed = new ArrayList<>();

        new BulkAsyncRunner(new BulkFailureTranslator(), registry).run(job, List.of(1L, 2L, 3L), id -> {
            processed.add(id);
            if (id == 1L) job.requestCancel();
        });

        assertThat(processed).containsExactly(1L);
        assertThat(job.toResource().status()).isEqualTo(BulkJobStatus.CANCELLED);
        assertThat(job.toResource().succeeded()).containsExactly(1L);
        verify(registry).finish(job);
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

        new BulkAsyncRunner(broken, registry).run(job, List.of(1L), id -> {
            throw new IllegalArgumentException("boom");
        });

        assertThat(job.toResource().status()).isEqualTo(BulkJobStatus.FAILED);
        assertThat(job.toResource().error()).isNotBlank();
        verify(registry).finish(job);
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

        job.complete();
        assertThat(job.toResource().succeeded()).containsExactly(1L);
    }
}
