package fr.siamois.ui.api.openapi.v1.generic.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.dto.TreePage;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListMetaTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aPlainPageHasNoMatchCountAndItIsNotSerialized() throws Exception {
        ListMeta meta = ListMeta.of(new PageImpl<>(List.of("a"), PageRequest.of(0, 10), 42), 10, 0);

        assertThat(meta.total()).isEqualTo(42);
        assertThat(meta.matchCount()).isNull();
        assertThat(json.writeValueAsString(meta)).doesNotContain("matchCount");
    }

    @Test
    void aTreePageKeepsTheRootsAsTotalAndAddsTheMatchCount() throws Exception {
        ListMeta meta = ListMeta.of(new TreePage<>(List.of("a"), PageRequest.of(0, 10), 25, 37), 10, 0);

        assertThat(meta.total()).isEqualTo(25); // the roots, which page the list
        assertThat(meta.matchCount()).isEqualTo(37);
        assertThat(json.writeValueAsString(meta)).contains("\"matchCount\":37").contains("\"total\":25");
    }
}
