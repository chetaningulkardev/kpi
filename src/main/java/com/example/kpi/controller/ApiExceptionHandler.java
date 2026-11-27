package com.example.kpi.controller;

import com.example.kpi.dto.ApiResponse;
import com.example.kpi.exception.InvalidCredentialsException;
import com.example.kpi.exception.InvalidTokenException;
import com.example.kpi.exception.RoleNotFoundException;
import com.example.kpi.exception.UserAlreadyExistsException;
import com.example.kpi.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * Maps exceptions to HTTP responses.
 * <p>
 * Spring dispatches to the most specific handler for a given exception type, so
 * the narrow handlers below take precedence over {@link #handleRuntime}. That
 * ordering is what keeps the catch-all from flattening everything to 400.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * Bean validation errors on @Valid request bodies.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message.isBlank() ? "Validation failed" : message, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to perform this action", HttpStatus.FORBIDDEN.value()));
    }

    /**
     * Authorisation denials raised by the service layer.
     * <p>
     * The workflow services signal "you may not touch this record" with
     * {@link SecurityException} — the evidence ownership checks, the
     * reviewer-scope checks and the status-history guard all do. Because
     * {@code SecurityException} is a {@code RuntimeException}, every one of
     * them used to fall through to the catch-all and surface as 400, which is
     * both wrong for the client and invisible to anything alerting on 403s.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurity(SecurityException ex) {
        String message = ex.getMessage() != null
                ? ex.getMessage()
                : "You do not have permission to perform this action";
        log.warn("Authorisation denied: {}", message);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(message, HttpStatus.FORBIDDEN.value()));
    }

    /**
     * Failed sign-in and expired/forged tokens.
     * <p>
     * 401 is what tells the client to re-authenticate; as a 400 the frontend's
     * token-refresh interceptor never triggered, because it keys on 401.
     * <p>
     * The message is whatever the service chose — {@code AuthenticationService}
     * deliberately says only "Invalid email or password", never which of the two
     * was wrong, so an attacker cannot use the response to enumerate accounts.
     */
    @ExceptionHandler({InvalidCredentialsException.class, InvalidTokenException.class})
    public ResponseEntity<ApiResponse<Object>> handleUnauthenticated(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Authentication failed";
        log.warn("Authentication failed: {}", message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(message, HttpStatus.UNAUTHORIZED.value()));
    }

    /**
     * Records that genuinely do not exist. 404 lets the UI show "this was
     * removed" rather than treating it as a malformed request.
     */
    @ExceptionHandler({UserNotFoundException.class, RoleNotFoundException.class})
    public ResponseEntity<ApiResponse<Object>> handleNotFound(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Not found";
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(message, HttpStatus.NOT_FOUND.value()));
    }

    /**
     * Duplicate registration — a conflict with existing state, not bad input.
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleAlreadyExists(UserAlreadyExistsException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "That record already exists";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(message, HttpStatus.CONFLICT.value()));
    }

    /**
     * Lost update caught by the {@code @Version} column on {@code Kpi}.
     * <p>
     * This is the concurrent-review case: two managers open the same KPI and
     * both submit. The second write is rejected rather than silently
     * overwriting the first, so the client needs to reload — which is a 409,
     * not a 400, and needs a message a reviewer can act on rather than
     * Hibernate's "Row was updated or deleted by another transaction".
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "This record was changed by someone else while you were editing it. "
                                + "Reload and apply your changes again.",
                        HttpStatus.CONFLICT.value()));
    }

    /**
     * Constraint violations from the database.
     * <p>
     * The exception message carries the raw constraint name and often the
     * offending values, so it is logged rather than returned.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        "This change conflicts with existing data. It may already exist, "
                                + "or something depends on the record you are removing.",
                        HttpStatus.CONFLICT.value()));
    }

    /**
     * Evidence upload larger than the configured multipart limit.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("The file is too large to upload.",
                        HttpStatus.PAYLOAD_TOO_LARGE.value()));
    }

    /**
     * A URL that maps to no handler at all.
     * <p>
     * Without this, Spring's static-resource fallback tries to serve the path
     * as a file, fails, and the miss lands in the catch-all below as a 500.
     * A mistyped endpoint is not a server defect: it told callers the backend
     * was broken when nothing was wrong, and it filled the error log with
     * stack traces that hid real faults.
     * <p>
     * Logged at debug, not error. Unmatched paths are mostly scanners and
     * stale clients, and at warn they would drown the signal they are meant
     * to provide.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        log.debug("No handler for {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("No such endpoint.", HttpStatus.NOT_FOUND.value()));
    }

    /**
     * The endpoint exists but does not accept this verb — a GET where only
     * POST is mapped. 405 rather than 404, because the path is real and the
     * response carries an {@code Allow} header naming what is permitted.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.debug("Method {} not supported for this endpoint", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(
                        "This endpoint does not accept " + ex.getMethod() + " requests.",
                        HttpStatus.METHOD_NOT_ALLOWED.value()));
    }

    /**
     * The request is missing something the endpoint declares as required — a
     * header, a query parameter, a multipart part — or supplied it in a form
     * that will not convert.
     * <p>
     * These reached the catch-all and were reported as 500. A caller who
     * forgot an {@code Authorization} header was told the server had failed,
     * which sends them to the wrong team with the wrong question.
     * <p>
     * Spring's own message names the missing item without echoing any value,
     * so it is safe to return and is the one piece of information that makes
     * the response actionable.
     */
    @ExceptionHandler({ServletRequestBindingException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Object>> handleBadRequestBinding(Exception ex) {
        log.debug("Malformed request: {}", ex.getMessage());
        String message = switch (ex) {
            case MissingRequestHeaderException e ->
                    "Required header '" + e.getHeaderName() + "' is missing.";
            case MissingServletRequestParameterException e ->
                    "Required parameter '" + e.getParameterName() + "' is missing.";
            case MissingServletRequestPartException e ->
                    "Required file part '" + e.getRequestPartName() + "' is missing.";
            // Deliberately does not include the rejected value: it is caller
            // supplied and ends up rendered by the client.
            case MethodArgumentTypeMismatchException e ->
                    "Parameter '" + e.getName() + "' is not in the expected format.";
            default -> "The request is missing required information.";
        };
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * Body present but unparseable, or absent where one is required.
     * <p>
     * The parser's message quotes the offending fragment of the payload, which
     * is caller-supplied, so it is logged rather than returned.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("The request body is missing or malformed.",
                        HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * Business rule violations that depend on current state rather than on the
     * request itself — reviewing a KPI in the wrong status, deleting evidence
     * from an approved KPI. 409 distinguishes "valid request, wrong moment"
     * from a malformed one.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalState(IllegalStateException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "This action is not allowed right now";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(message, HttpStatus.CONFLICT.value()));
    }

    /**
     * Defects, not client mistakes.
     * <p>
     * These are always bugs. Returning 400 with the exception's message hid
     * them from anyone watching the 5xx rate and leaked internals — a
     * {@code NullPointerException} message names fields and types. They get a
     * logged stack trace and an opaque 500 instead.
     */
    @ExceptionHandler({
            NullPointerException.class,
            IndexOutOfBoundsException.class,
            ClassCastException.class,
            ArithmeticException.class,
            NumberFormatException.class
    })
    public ResponseEntity<ApiResponse<Object>> handleProgrammingError(RuntimeException ex) {
        log.error("Unhandled defect while serving request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Something went wrong. Please try again.",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    /**
     * Catch-all for service-level business rule violations, which this codebase
     * raises as plain {@link RuntimeException}. The real message is returned so
     * the UI can display it instead of a masked 401.
     * <p>
     * Kept at 400 deliberately: the narrower handlers above already divert the
     * cases where that status would be wrong.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntime(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Request failed";
        log.warn("Request rejected: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * Anything checked that escaped a service. Always a defect.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex) {
        log.error("Unexpected error while serving request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Something went wrong. Please try again.",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
}

