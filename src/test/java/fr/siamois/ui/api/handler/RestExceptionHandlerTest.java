package fr.siamois.ui.api.handler;

import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.identifier.ProjectIdentifierConstraints;
import fr.siamois.domain.services.identifier.IdentifierRangeExhaustedException;
import fr.siamois.ui.api.openapi.v1.exception.IdentifierConflictException;
import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictData;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = TestRestExceptionHandlers.create();

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
        ResponseEntity<ApiError> response = handler.unhandled(new RuntimeException("boom secret"), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("internal_error");
        assertThat(response.getBody().correlationId()).isNotBlank();
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    @Test
    void forbiddenOperation_returns403NotAnInternalError() {
        ResponseEntity<ApiError> response = handler.forbiddenOperation(
                new fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException("no"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error()).isEqualTo("forbidden");
    }

    private static DataIntegrityViolationException uniqueViolation(String constraint) {
        SQLException sql = new SQLException("duplicate key value violates unique constraint \"" + constraint + "\"", "23505");
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", sql, constraint));
    }

    @Test
    void identifierUniquenessViolation_returns409() {
        ResponseEntity<ApiError> response = handler.unhandled(uniqueViolation(ProjectIdentifierConstraints.SPECIMEN),
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("conflict");
        // No Accept-Language: the API's default language, French.
        assertThat(response.getBody().message()).isEqualTo("Cet identifiant existe déjà dans le projet");
    }

    @Test
    void identifierUniquenessViolation_isAnsweredInTheCallersLanguage() {
        ResponseEntity<ApiError> response = handler.unhandled(uniqueViolation(ProjectIdentifierConstraints.PHASE),
                requestIn("en-GB,en;q=0.9"));

        assertThat(response.getBody().message()).isEqualTo("This identifier already exists in the project");
    }

    @Test
    void identifierConflict_returns409NamingTheIdentifierInTheCallersLanguage() {
        ResponseEntity<ApiError> fr = handler.identifierConflict(
                new IdentifierConflictException("UE-12", null), requestIn("fr-FR"));
        ResponseEntity<ApiError> en = handler.identifierConflict(
                new IdentifierConflictException("UE-12", null), requestIn("en"));

        assertThat(fr.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fr.getBody().error()).isEqualTo("conflict");
        assertThat(fr.getBody().message()).isEqualTo("L'identifiant UE-12 existe déjà dans le projet");
        assertThat(en.getBody().message()).isEqualTo("Identifier UE-12 already exists in the project");
    }

    @Test
    void identifierConflict_withoutIdentifier_usesTheGenericMessage() {
        ResponseEntity<ApiError> response = handler.identifierConflict(new IdentifierConflictException(), requestIn("en"));

        assertThat(response.getBody().message()).isEqualTo("This identifier already exists in the project");
    }

    @Test
    void identifierRangeExhausted_returns409InTheCallersLanguage() {
        ResponseEntity<ApiError> fr = handler.identifierRangeExhausted(
                new IdentifierRangeExhaustedException("exhausted"), requestIn("fr-FR"));
        ResponseEntity<ApiError> en = handler.identifierRangeExhausted(
                new IdentifierRangeExhaustedException("exhausted"), requestIn("en"));

        assertThat(fr.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fr.getBody().error()).isEqualTo("conflict");
        assertThat(fr.getBody().message()).isEqualTo(
                "Impossible de générer de nouveaux identifiants : la valeur maximale configurée a été atteinte");
        assertThat(en.getBody().message()).isEqualTo(
                "No new identifier can be generated: the configured maximum value has been reached");
    }

    private static MockHttpServletRequest requestIn(String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", acceptLanguage);
        return request;
    }

    @Test
    void identifierUniquenessViolation_wrappedByASave_returns409() {
        ResponseEntity<ApiError> response = handler.unhandled(new FailedRecordingUnitSaveException("échec",
                uniqueViolation(ProjectIdentifierConstraints.RECORDING_UNIT)), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void otherIntegrityViolation_staysAnInternalError() {
        ResponseEntity<ApiError> response = handler.unhandled(uniqueViolation("uk_document_import_item_client_key"),
                new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
