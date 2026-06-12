package fr.siamois.ui.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Réponse JSON 401 homogène pour filtre JWT et point d’entrée Spring Security (API OpenAPI).
 */
public final class ApiUnauthorizedJsonWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiUnauthorizedJsonWriter() {
    }

    public static void write(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = (message != null && !message.isBlank()) ? message : "Authentication required";
        MAPPER.writeValue(response.getOutputStream(), Map.of(
                "error", "unauthorized",
                "message", body));
    }
}
