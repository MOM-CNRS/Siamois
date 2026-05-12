package fr.siamois.ui.api.openapi.v1;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
