package fr.siamois.ui.api.openapi.v1;

import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.mobile.RecordingUnitMobileDetailData;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenApiRestExceptionHandlerTest {

    private final OpenApiRestExceptionHandler handler = new OpenApiRestExceptionHandler();

    @Test
    void badCredentials_returns401() {
        ResponseEntity<Map<String, String>> response =
                handler.badCredentials(new BadCredentialsException("Invalid credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "unauthorized");
    }

    @Test
    void messageNotReadable_returns400() {
        ResponseEntity<Map<String, String>> response =
                handler.messageNotReadable(mock(HttpMessageNotReadableException.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "bad_request");
    }

    @Test
    void syncRevisionConflict_returns409WithPayload() {
        SyncConflictData data = new SyncConflictData(
                "recording_unit", "42", 1L, 2L, mock(RecordingUnitMobileDetailData.class));
        SyncRevisionConflictException ex = new SyncRevisionConflictException(data);

        ResponseEntity<?> response = handler.syncRevisionConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void responseStatus_unauthorized_mapsErrorKey() {
        ResponseEntity<Map<String, String>> response = handler.responseStatus(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "unauthorized");
    }

    @Test
    void responseStatus_otherStatus_mapsGenericError() {
        ResponseEntity<Map<String, String>> response = handler.responseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Interdit"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "error");
        assertThat(response.getBody()).containsEntry("message", "Interdit");
    }

    @Test
    void unhandled_returns500WithCorrelationId() {
        ResponseEntity<Map<String, String>> response = handler.unhandled(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "internal_error");
        assertThat(response.getBody()).containsKey("correlationId");
    }
}
