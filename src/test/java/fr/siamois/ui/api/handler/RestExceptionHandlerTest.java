package fr.siamois.ui.api.handler;

import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictData;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void badCredentials_returns401WithUnauthorizedCode() {
        ResponseEntity<ApiError> response = handler.badCredentials(new BadCredentialsException("Invalid credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("unauthorized");
    }

    @Test
    void badCredentials_nullMessage_usesDefault() {
        ResponseEntity<ApiError> response = handler.badCredentials(new BadCredentialsException(null));

        assertThat(response.getBody().message()).isEqualTo("Invalid credentials");
    }

    @Test
    void domainNotFound_returns404() {
        ResponseEntity<ApiError> response = handler.notFound(new RecordingUnitNotFoundException("absente"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("not_found");
    }

    @Test
    void messageNotReadable_returns400() {
        ResponseEntity<ApiError> response = handler.messageNotReadable(mock(HttpMessageNotReadableException.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("bad_request");
    }

    @Test
    void missingRequestParameter_returns400NamingTheParameter() {
        ResponseEntity<ApiError> response = handler.missingRequestParameter(
                new MissingServletRequestParameterException("projectId", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("validation_failed");
        assertThat(response.getBody().details()).containsExactly(new ApiError.Detail("projectId", "required"));
    }

    @Test
    void mediaTypeNotSupported_returns415() {
        ResponseEntity<ApiError> response = handler.mediaTypeNotSupported(new HttpMediaTypeNotSupportedException("text/plain"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().error()).isEqualTo("unsupported_media_type");
    }

    @Test
    void syncRevisionConflict_returns409WithErrorCodeAndServerState() {
        SyncConflictData data = new SyncConflictData("recording-units", "42", 1L, 2L, new Object());

        ResponseEntity<SyncConflictResponse> response = handler.syncRevisionConflict(new SyncRevisionConflictException(data));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getError()).isEqualTo("conflict");
        assertThat(response.getBody().getData()).isSameAs(data);
    }

    @Test
    void responseStatus_mapsEachStatusToItsStableCode() {
        assertThat(handler.responseStatus(new ResponseStatusException(HttpStatus.FORBIDDEN, "Interdit")).getBody())
                .isEqualTo(ApiError.of("forbidden", "Interdit"));
        assertThat(handler.responseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "Absent")).getBody().error())
                .isEqualTo("not_found");
        assertThat(handler.responseStatus(new ResponseStatusException(HttpStatus.CONFLICT, "Pris")).getBody().error())
                .isEqualTo("conflict");
        assertThat(handler.responseStatus(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non")).getBody().error())
                .isEqualTo("unauthorized");
        assertThat(handler.responseStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Non")).getBody().error())
                .isEqualTo("bad_request");
    }

    @Test
    void responseStatus_blankReason_defaultsTheMessage() {
        assertThat(handler.responseStatus(new ResponseStatusException(HttpStatus.UNAUTHORIZED, " ")).getBody().message())
                .isEqualTo("Unauthorized");
        assertThat(handler.responseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, null)).getBody().message())
                .isEqualTo("Error");
    }

    @Test
    void validationException_carriesItsDetails() {
        ApiValidationException ex = new ApiValidationException("Filtre invalide",
                List.of(new ApiError.Detail("f.type", "valeur inconnue")));

        ResponseEntity<ApiError> response = handler.validation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("validation_failed");
        assertThat(response.getBody().details()).hasSize(1);
    }

    @Test
    void unhandled_returns500WithCorrelationIdAndNoLeak() {
        ResponseEntity<ApiError> response = handler.unhandled(new RuntimeException("boom secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("internal_error");
        assertThat(response.getBody().correlationId()).isNotBlank();
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}
