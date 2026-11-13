package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.domain.models.bulk.BulkJobRecord;
import fr.siamois.infrastructure.database.repositories.bulk.BulkJobRepository;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobResource;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkJobStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Mock
    private BulkJobRepository repository;

    private BulkJobStore store;

    /**
     * A store over a mocked repository.
     */
    @BeforeEach
    void setUp() {
        store = new BulkJobStore(repository);
    }

    /**
     * A record of person 7 in this status, last written at {@code updatedAt}.
     */
    private static BulkJobRecord record(String status, Instant updatedAt) {
        BulkJobRecord record = new BulkJobRecord();
        record.setId("job-1");
        record.setOwnerId(7L);
        record.setStatus(status);
        record.setTotal(3);
        record.setSucceededCount(1);
        record.setUpdatedAt(updatedAt);
        record.setCreatedAt(updatedAt);
        return record;
    }

    /**
     * A second job while one runs is refused.
     */
    @Test
    void refusesASecondRunningJob() {
        when(repository.findByOwnerIdAndStatus(7L, "RUNNING")).thenReturn(List.of(record("RUNNING", NOW)));
        BulkJob job = new BulkJob(7L, 2);

        assertThatThrownBy(() -> store.insertRunning(job, NOW))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * Two starts racing past the check: the unique index refuses the second one.
     */
    @Test
    void refusesARaceTheIndexCaught() {
        when(repository.findByOwnerIdAndStatus(7L, "RUNNING")).thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_bulk_job_running_person"));
        BulkJob job = new BulkJob(7L, 2);

        assertThatThrownBy(() -> store.insertRunning(job, NOW))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * A running job silent for too long died with its instance: it no longer blocks a new job.
     */
    @Test
    void aStaleRunningJobNoLongerBlocks() {
        BulkJobRecord stale = record("RUNNING", NOW.minus(BulkJobStore.STALE_AFTER).minusSeconds(1));
        when(repository.findByOwnerIdAndStatus(7L, "RUNNING")).thenReturn(List.of(stale)).thenReturn(List.of());
        BulkJob job = new BulkJob(7L, 2);

        store.insertRunning(job, NOW);

        assertThat(stale.getStatus()).isEqualTo("FAILED");
        assertThat(stale.getError()).isEqualTo(BulkJobStore.INTERRUPTED);
    }

    /**
     * A job read back while it runs gives its counts; one silent for too long is reported as interrupted.
     */
    @Test
    void readsARunningJobOrReportsItInterrupted() {
        when(repository.findByIdAndOwnerId("job-1", 7L)).thenReturn(Optional.of(record("RUNNING", NOW)));
        assertThat(store.find("job-1", 7L, NOW)).hasValueSatisfying(job -> {
            assertThat(job.status()).isEqualTo(BulkJobStatus.RUNNING);
            assertThat(job.processed()).isEqualTo(1);
        });

        when(repository.findByIdAndOwnerId("job-1", 7L)).thenReturn(Optional.of(record("RUNNING", NOW.minusSeconds(3600))));
        assertThat(store.find("job-1", 7L, NOW)).hasValueSatisfying(job -> assertThat(job.status()).isEqualTo(BulkJobStatus.FAILED));
    }

    /**
     * How a job ended is written, its lists included, and read back as such.
     */
    @Test
    void writesAndReadsBackHowAJobEnded() {
        BulkJobRecord record = record("RUNNING", NOW);
        when(repository.findById("job-1")).thenReturn(Optional.of(record));
        BulkJobResource ended = new BulkJobResource("job-1", BulkJobStatus.CANCELLED, 3, 2, 1, 1, List.of(4L),
                List.of(new BulkFailureResource(5L, 409, "Relations")), null);

        store.saveEnd(ended, NOW);
        when(repository.findByIdAndOwnerId("job-1", 7L)).thenReturn(Optional.of(record));

        assertThat(store.find("job-1", 7L, NOW)).hasValueSatisfying(job -> {
            assertThat(job.status()).isEqualTo(BulkJobStatus.CANCELLED);
            assertThat(job.succeeded()).containsExactly(4L);
            assertThat(job.failed()).containsExactly(new BulkFailureResource(5L, 409, "Relations"));
        });
    }

    /**
     * A stop is recorded only on a running job.
     */
    @Test
    void recordsAStopOnARunningJobOnly() {
        BulkJobRecord running = record("RUNNING", NOW);
        when(repository.findByIdAndOwnerId("job-1", 7L)).thenReturn(Optional.of(running));
        assertThat(store.requestCancel("job-1", 7L)).isTrue();
        assertThat(running.isCancelRequested()).isTrue();

        when(repository.findByIdAndOwnerId("job-1", 7L)).thenReturn(Optional.of(record("DONE", NOW)));
        assertThat(store.requestCancel("job-1", 7L)).isFalse();
    }
}
