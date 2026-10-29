package fr.siamois.ui.api.handler;

import fr.siamois.domain.models.exceptions.HierarchyCycleException;
import fr.siamois.domain.models.exceptions.actionunit.ActionUnitNotFoundException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitNotFoundException;
import fr.siamois.domain.models.identifier.ProjectIdentifierConstraints;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.identifier.IdentifierRangeExhaustedException;
import fr.siamois.ui.api.openapi.v1.exception.IdentifierConflictException;
import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Answers every error of {@code /api/v1} as an {@link ApiError}: the same body whatever raised it, with a stable
 * {@code error} code. The 401 of the security filters ({@code ApiUnauthorizedJsonWriter}) uses the same body.
 */
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "fr.siamois.ui.api.openapi.v1")
public class RestExceptionHandler {

    public static final String BAD_REQUEST = "bad_request";
    public static final String VALIDATION_FAILED = "validation_failed";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String FORBIDDEN = "forbidden";
    public static final String NOT_FOUND = "not_found";
    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";
    public static final String CONFLICT = "conflict";
    public static final String PAYLOAD_TOO_LARGE = "payload_too_large";
    public static final String UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";
    public static final String INTERNAL_ERROR = "internal_error";

    static final String IDENTIFIER_CONFLICT_KEY = "api.error.identifier.alreadyExists";
    static final String IDENTIFIER_CONFLICT_NAMED_KEY = "api.error.identifier.alreadyExistsNamed";
    static final String IDENTIFIER_RANGE_EXHAUSTED_KEY = "api.error.identifier.rangeExhausted";

    private final LangService langService;

    /** The stable code of an HTTP status. */
    public static String codeOf(HttpStatusCode status) {
        int value = status.value();
        return switch (value) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> value >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
        };
    }

    private static ResponseEntity<ApiError> answer(HttpStatusCode status, ApiError body) {
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> badCredentials(BadCredentialsException ex) {
        return answer(HttpStatus.UNAUTHORIZED, ApiError.of(UNAUTHORIZED,
                ex.getMessage() != null ? ex.getMessage() : "Invalid credentials"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied() {
        return answer(HttpStatus.FORBIDDEN, ApiError.of(FORBIDDEN, "Accès refusé"));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiError> forbiddenOperation(ForbiddenOperationException ex) {
        return answer(HttpStatus.FORBIDDEN, ApiError.of(FORBIDDEN, "Accès refusé"));
    }

    @ExceptionHandler({RecordingUnitNotFoundException.class, ActionUnitNotFoundException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException ex) {
        return answer(HttpStatus.NOT_FOUND, ApiError.of(NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingRequestParameter(MissingServletRequestParameterException ex) {
        String message = "Required request parameter '" + ex.getParameterName() + "' is missing";
        return answer(HttpStatus.BAD_REQUEST, new ApiError(VALIDATION_FAILED, message,
                List.of(new ApiError.Detail(ex.getParameterName(), "required")), null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        return answer(HttpStatus.BAD_REQUEST, new ApiError(VALIDATION_FAILED, message,
                List.of(new ApiError.Detail(ex.getName(), "invalid value")), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException ex) {
        List<ApiError.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.Detail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return answer(HttpStatus.BAD_REQUEST, new ApiError(VALIDATION_FAILED, "Invalid request body", details, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException ex) {
        List<ApiError.Detail> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.Detail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return answer(HttpStatus.BAD_REQUEST, new ApiError(VALIDATION_FAILED, "Invalid request", details, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> messageNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        log.debug("API request body not readable: {}", cause.getMessage());
        return answer(HttpStatus.BAD_REQUEST, ApiError.of(BAD_REQUEST,
                "Invalid or malformed JSON body. Use strict JSON with double-quoted keys and strings, "
                        + "e.g. {\"name\":\"value\"}. Ensure Content-Type is application/json."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> mediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return answer(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiError.of(UNSUPPORTED_MEDIA_TYPE,
                ex.getMessage() != null ? ex.getMessage() : "Content-Type not supported. Use application/json."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return answer(HttpStatus.METHOD_NOT_ALLOWED, ApiError.of(METHOD_NOT_ALLOWED, ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> uploadTooLarge() {
        return answer(HttpStatus.PAYLOAD_TOO_LARGE, ApiError.of(PAYLOAD_TOO_LARGE, "Le fichier dépasse la taille maximale"));
    }

    /** The revision a client edited from is no longer the server's: the 409 also carries the server state. */
    @ExceptionHandler(SyncRevisionConflictException.class)
    public ResponseEntity<SyncConflictResponse> syncRevisionConflict(SyncRevisionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new SyncConflictResponse(ex.getConflictData()));
    }

    @ExceptionHandler(IdentifierConflictException.class)
    public ResponseEntity<ApiError> identifierConflict(IdentifierConflictException ex, HttpServletRequest request) {
        return answer(HttpStatus.CONFLICT, ApiError.of(CONFLICT, identifierConflictMessage(ex.getIdentifier(), request)));
    }

    /** The identifier range of the form config is used up: no creation can get an identifier any more. */
    @ExceptionHandler(IdentifierRangeExhaustedException.class)
    public ResponseEntity<ApiError> identifierRangeExhausted(IdentifierRangeExhaustedException ex,
                                                             HttpServletRequest request) {
        log.debug("Identifier range exhausted", ex);
        return answer(HttpStatus.CONFLICT, ApiError.of(CONFLICT,
                langService.msg(IDENTIFIER_RANGE_EXHAUSTED_KEY, requestLocale(request))));
    }

    /** "Identifier already in the project", in the language of the request's {@code Accept-Language}. */
    private String identifierConflictMessage(String identifier, HttpServletRequest request) {
        Locale locale = requestLocale(request);
        return identifier == null
                ? langService.msg(IDENTIFIER_CONFLICT_KEY, locale)
                : langService.msg(IDENTIFIER_CONFLICT_NAMED_KEY, locale, identifier);
    }

    private Locale requestLocale(HttpServletRequest request) {
        return langService.localeForApiLang(
                ProjectApiService.primaryAcceptLanguage(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)));
    }

    /** A hierarchy link that would close a loop: the message says so. */
    @ExceptionHandler(HierarchyCycleException.class)
    public ResponseEntity<ApiError> hierarchyCycle(HierarchyCycleException ex) {
        return answer(HttpStatus.CONFLICT, ApiError.of(CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(ApiValidationException.class)
    public ResponseEntity<ApiError> validation(ApiValidationException ex) {
        return answer(ex.getStatusCode(), new ApiError(VALIDATION_FAILED, ex.getReason(), ex.getDetails(), null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = status.value() == HttpStatus.UNAUTHORIZED.value() ? "Unauthorized" : "Error";
        }
        return answer(status, ApiError.of(codeOf(status), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unhandled(Exception ex, HttpServletRequest request) {
        // An identifier already used in its project, caught by the database (two writes raced past the
        // service's check) — raw, or wrapped by a save or a commit: the cause chain tells.
        if (ProjectIdentifierConstraints.isViolatedBy(ex)) {
            log.debug("Project identifier uniqueness violated", ex);
            return answer(HttpStatus.CONFLICT, ApiError.of(CONFLICT, identifierConflictMessage(null, request)));
        }
        String correlationId = UUID.randomUUID().toString();
        log.error("[{}] Unhandled exception in API", correlationId, ex);
        return answer(HttpStatus.INTERNAL_SERVER_ERROR,
                new ApiError(INTERNAL_ERROR, "Internal Server Error", null, correlationId));
    }
}
