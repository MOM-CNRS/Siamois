package fr.siamois.infrastructure.database.repositories.misc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchSqlTest {

    private String sql() throws IOException {
        try (var stream = getClass().getResourceAsStream("/pgplsql/global_search.sql")) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void function_searchesTheFourTypesAndCountsBeforeCutting() throws IOException {
        String sql = sql();
        assertThat(sql)
                .contains("'project'", "'recordingUnit'", "'find'", "'place'")
                .contains("count(*) OVER ()")
                .contains("r.rn <= p_limit");
    }

    @Test
    void function_matchesAnywhereAndEscapesLikeWildcards() throws IOException {
        assertThat(sql()).contains("'%' || replace(replace(replace(p_input");
    }

    @Test
    void function_scopesToInstitutionAndVisibleProjects() throws IOException {
        String sql = sql();
        assertThat(sql).contains("= p_institution_id").contains("= ANY (p_visible_project_ids)");
    }
}
