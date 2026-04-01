package fr.siamois.ui.api.handler;

import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RestExceptionHandlerTest {

    RestExceptionHandler handler = new RestExceptionHandler();

    @Mock
    HttpServletRequest request;

    @Test
    void handleNotFound() {
        ResponseEntity<ApiError> result = handler.handleNotFound(new RecordingUnitNotFoundException("Error"), request);
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleResponseStatusException() {
        ResponseEntity<ApiError> result = handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.FORBIDDEN), request);
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleGenericException() {
        ResponseEntity<ApiError> result = handler.handleGenericException(new Exception("Error"), request);
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}