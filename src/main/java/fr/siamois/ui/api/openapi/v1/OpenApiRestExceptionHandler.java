package fr.siamois.ui.api.openapi.v1;

import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Order(1)
@RestControllerAdvice(basePackages = "fr.siamois.ui.api.openapi.v1.controller")
public class OpenApiRestExceptionHandler {

    public static final String STRING = "error";
    public static final String MESSAGE = "message";

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> badCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(401).body(Map.of(
                STRING, "unauthorized",
                MESSAGE, ex.getMessage() != null ? ex.getMessage() : "Invalid credentials"));
    }

    /**
     * Corps JSON illisible (clés non quotées, virgule en trop, mauvais Content-Type, etc.).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> messageNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        log.debug("OpenAPI request body not readable: {}", cause != null ? cause.getMessage() : ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                STRING, "bad_request",
                MESSAGE,
                "Invalid or malformed JSON body. Use strict JSON with double-quoted keys and strings, "
                        + "e.g. {\"name\":\"value\"}. Ensure Content-Type is application/json."));
    }

    @ExceptionHandler(SyncRevisionConflictException.class)
    public ResponseEntity<SyncConflictResponse> syncRevisionConflict(
            SyncRevisionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new SyncConflictResponse(ex.getConflictData()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responseStatus(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = code == HttpStatus.UNAUTHORIZED.value() ? "Unauthorized" : "Error";
        }
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                STRING, code == HttpStatus.UNAUTHORIZED.value() ? "unauthorized" : STRING,
                MESSAGE, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unhandled(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("[{}] Unhandled exception in OpenAPI controller", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                STRING, "internal_error",
                MESSAGE, "Internal Server Error",
                "correlationId", correlationId));
    }
}
