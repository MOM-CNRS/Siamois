package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.ui.api.openapi.v1.response.bulk.BulkJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkJobRegistryTest {

    @Mock
    private BulkJobStore store;

    private BulkJobRegistry registry;

    /**
     * A registry over a mocked store.
     */
    @BeforeEach
    void setUp() {
        registry = new BulkJobRegistry(store);
    }

    /**
     * A running job is followed live by the one who started it, and only by them.
     */
    @Test
    void followsARunningJobLiveForItsOwnerOnly() {
        BulkJob job = registry.start(7L, 3);

        assertThat(registry.find(job.id(), 7L)).hasValueSatisfying(r -> assertThat(r.status()).isEqualTo(BulkJobStatus.RUNNING));
        assertThat(registry.find(job.id(), 8L)).isEmpty();
        verify(store, never()).find(anyString(), anyLong(), any());
    }

    /**
     * An ended job, or one running elsewhere, is read from the database.
     */
    @Test
    void readsAJobNotRunningHereFromTheDatabase() {
        when(store.find(anyString(), anyLong(), any())).thenReturn(Optional.empty());

        assertThat(registry.find("elsewhere", 7L)).isEmpty();
        verify(store).find(anyString(), anyLong(), any());
    }

    /**
     * A second job while one runs is refused, as the store says.
     */
    @Test
    void refusesASecondJobWhileOneRuns() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "déjà en cours")).when(store).insertRunning(any(), any());

        assertThatThrownBy(() -> registry.start(7L, 3))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * A stop asked for a job running here reaches it at once.
     */
    @Test
    void aStopReachesAJobRunningHere() {
        BulkJob job = registry.start(7L, 3);

        assertThat(registry.cancel(job.id(), 7L)).isTrue();
        assertThat(job.cancelRequested()).isTrue();
    }

    /**
     * A stop asked on another instance is picked up when the job writes its progress.
     */
    @Test
    void aStopAskedElsewhereIsPickedUpWithTheProgress() {
        BulkJob job = registry.start(7L, 3);
        when(store.saveProgress(any(), any())).thenReturn(true);

        registry.saveProgress(job);

        assertThat(job.cancelRequested()).isTrue();
    }

    /**
     * An ended job is written to the database and no longer runs here.
     */
    @Test
    void anEndedJobIsWrittenAndLeavesTheInstance() {
        BulkJob job = registry.start(7L, 3);
        job.complete();
        when(store.find(anyString(), anyLong(), any())).thenReturn(Optional.empty());

        registry.finish(job);

        verify(store).saveEnd(any(), any());
        assertThat(registry.find(job.id(), 7L)).isEmpty();
    }
}
