package fr.siamois.ui.api.handler;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * A 400 that names the parameters or fields at fault, answered as {@code validation_failed} with
 * {@link ApiError#details()}.
 */
public class ApiValidationException extends ResponseStatusException {

    private final transient List<ApiError.Detail> details;

    public ApiValidationException(String message, List<ApiError.Detail> details) {
        super(HttpStatus.BAD_REQUEST, message);
        this.details = List.copyOf(details);
    }

    public List<ApiError.Detail> getDetails() {
        return details;
    }
}
