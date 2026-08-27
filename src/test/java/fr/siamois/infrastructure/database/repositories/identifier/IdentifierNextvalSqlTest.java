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
            assertThat(sql).contains("DO UPDATE SET counter = identifier_counter.counter + 1");
            assertThat(sql).contains("RETURNING counter - 1");
            assertThat(sql).doesNotContain("SELECT identifier_counter_id");
        }
    }
}
