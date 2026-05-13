package fr.siamois.ui.api.openapi.v1;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Order(1)
@RestControllerAdvice(basePackages = "fr.siamois.ui.api.openapi.v1.controller")
public class OpenApiRestExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> badCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(401).body(Map.of(
                "error", "unauthorized",
                "message", ex.getMessage() != null ? ex.getMessage() : "Invalid credentials"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responseStatus(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = code == HttpStatus.UNAUTHORIZED.value() ? "Unauthorized" : "Error";
        }
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", code == HttpStatus.UNAUTHORIZED.value() ? "unauthorized" : "error",
                "message", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unhandled(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "internal_error",
                "message", ex.getMessage() != null ? ex.getMessage() : "Internal Server Error"));
    }
}
