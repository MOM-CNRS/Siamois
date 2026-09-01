package fr.siamois.infrastructure.database.repositories.identifier;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierNextvalSqlTest {
    @Test
    void function_shouldAllocateThroughOneAtomicUpsert() throws IOException {
        try (var stream = getClass().getResourceAsStream("/pgplsql/identifier_nextval.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("ON CONFLICT (fk_action_unit_id, fk_form_config_id, canonical_key)");
            assertThat(sql).contains("DO UPDATE SET counter = GREATEST(identifier_counter.counter, p_min_code) + 1");
            assertThat(sql).contains("RETURNING counter - 1");
            assertThat(sql).doesNotContain("SELECT identifier_counter_id");
        }
    }

    @Test
    void function_shouldSeedANewCounterAtTheConfiguredLowerBound() throws IOException {
        assertThat(sql()).contains("p_min_code + 1");
    }

    @Test
    void function_shouldRaiseARunningCounterUpToTheConfiguredLowerBound() throws IOException {
        // A counter created under a previous configuration must not keep allocating below the
        // lower bound the project configured afterwards.
        assertThat(sql()).contains("GREATEST(identifier_counter.counter, p_min_code)");
    }

    private String sql() throws IOException {
        try (var stream = getClass().getResourceAsStream("/pgplsql/identifier_nextval.sql")) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
