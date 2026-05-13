package fr.siamois.ui.config.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiUnauthorizedJsonWriterTest {

    @Test
    void write_escapesQuotesInMessage() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        ApiUnauthorizedJsonWriter.write(res, "bad \"quote\"");

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(res.getContentAsString()).contains("\\\"quote\\\"");
        assertThat(res.getContentAsString()).startsWith("{");
    }
}
