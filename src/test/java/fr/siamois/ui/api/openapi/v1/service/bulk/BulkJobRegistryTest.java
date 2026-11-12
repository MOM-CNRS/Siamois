package fr.siamois.ui.api.openapi.v1.service.bulk;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BulkJobRegistryTest {

    private final BulkJobRegistry registry = new BulkJobRegistry();

    /**
     * A job is found by the one who started it, and only by them.
     */
    @Test
    void findsAJobForItsOwnerOnly() {
        BulkJob job = registry.start(7L, 3);

        assertThat(registry.find(job.id(), 7L)).containsSame(job);
        assertThat(registry.find(job.id(), 8L)).isEmpty();
    }

    /**
     * A running job is kept; a job finished more than the result's lifetime ago is forgotten.
     */
    @Test
    void forgetsTheJobsFinishedLongAgo() {
        BulkJob running = registry.start(7L, 1);
        BulkJob finished = registry.start(7L, 1);
        Instant endedAt = Instant.parse("2026-01-01T10:00:00Z");
        finished.complete(endedAt);

        registry.purgeExpired(endedAt.plus(BulkJobRegistry.RESULT_TTL).plusSeconds(1));

        assertThat(registry.find(running.id(), 7L)).isPresent();
        assertThat(registry.find(finished.id(), 7L)).isEmpty();
    }
}
