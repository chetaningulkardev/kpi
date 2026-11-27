package com.example.kpi.controller;

import com.example.kpi.dto.ApiResponse;
import com.example.kpi.exception.InvalidCredentialsException;
import com.example.kpi.exception.InvalidTokenException;
import com.example.kpi.exception.RoleNotFoundException;
import com.example.kpi.exception.UserAlreadyExistsException;
import com.example.kpi.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status codes here are part of the API contract the UI branches on, and
 * every one of them used to be 400.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("Service authorisation denials map to 403, not 400")
    void securityExceptionIsForbidden() {
        // Regression: SecurityException is a RuntimeException, so it used to be
        // swallowed by the catch-all. Every evidence-ownership and reviewer-scope
        // guard in KpiReviewWorkflowService throws this.
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleSecurity(new SecurityException("You are not allowed to review this KPI"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getError()).isEqualTo("You are not allowed to review this KPI");
    }

    @Test
    @DisplayName("A SecurityException with no message still says something useful")
    void securityExceptionWithoutMessage() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleSecurity(new SecurityException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getError()).isNotBlank();
    }

    @Test
    @DisplayName("Concurrent review conflict maps to 409 with an actionable message")
    void optimisticLockIsConflict() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleOptimisticLock(
                new OptimisticLockingFailureException("Row was updated or deleted by another transaction"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Hibernate's own wording must not reach the reviewer.
        assertThat(response.getBody().getError())
                .doesNotContain("transaction")
                .contains("Reload");
    }

    @Test
    @DisplayName("Constraint violations map to 409 without leaking the constraint name")
    void dataIntegrityIsConflictAndOpaque() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uk_lookup_type_code\""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getError())
                .as("database internals must not reach the client")
                .doesNotContain("uk_lookup_type_code")
                .doesNotContain("duplicate key");
    }

    @Test
    @DisplayName("Oversized upload maps to 413")
    void uploadTooLarge() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleUploadTooLarge(new MaxUploadSizeExceededException(10_485_760L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("Wrong-state business rules map to 409 and keep their message")
    void illegalStateIsConflict() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleIllegalState(
                new IllegalStateException("Evidence cannot be removed once the KPI is approved"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getError())
                .isEqualTo("Evidence cannot be removed once the KPI is approved");
    }

    @Test
    @DisplayName("Defects map to 500 and reveal nothing")
    void programmingErrorsAreOpaqueServerErrors() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleProgrammingError(
                new NullPointerException("Cannot invoke \"User.getId()\" because \"reviewer\" is null"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError())
                .as("an NPE message names internal fields and types")
                .doesNotContain("getId")
                .doesNotContain("reviewer");
    }

    @Test
    @DisplayName("Business RuntimeExceptions keep returning their message at 400")
    void businessRuntimeExceptionsStillSurfaceTheirMessage() {
        // The UI displays this text, so the catch-all must stay as it was.
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleRuntime(new RuntimeException("Review cycle is already closed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Review cycle is already closed");
    }

    @Test
    @DisplayName("Bad input stays a 400")
    void illegalArgumentIsBadRequest() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Only PDF, JPG, JPEG, and PNG files are allowed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).contains("PDF");
    }

    @Test
    @DisplayName("Spring Security denials stay a 403")
    void accessDeniedIsForbidden() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleAccessDenied(new AccessDeniedException("Access is denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Bad credentials map to 401 so the client knows to re-authenticate")
    void invalidCredentialsIsUnauthorized() {
        // As a 400 this never reached the frontend's token-refresh interceptor,
        // which keys on 401.
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleUnauthenticated(new InvalidCredentialsException("Invalid email or password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Must not reveal which of the two was wrong — that enumerates accounts.
        assertThat(response.getBody().getError()).isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("Expired or forged tokens map to 401")
    void invalidTokenIsUnauthorized() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleUnauthenticated(new InvalidTokenException("Invalid or expired token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Missing records map to 404")
    void notFoundIsNotFound() {
        assertThat(handler.handleNotFound(new UserNotFoundException("User not found")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleNotFound(new RoleNotFoundException("Role not found")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Duplicate registration maps to 409")
    void alreadyExistsIsConflict() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleAlreadyExists(new UserAlreadyExistsException("Email already registered"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Checked exceptions that escape map to an opaque 500")
    void unexpectedExceptionsAreOpaque() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleUnexpected(new Exception("/var/secrets/keystore.p12 not readable"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError()).doesNotContain("keystore");
    }

    @Test
    @DisplayName("An unmapped URL is 404, not a 500 blaming the server")
    void unknownPathIsNotFound() throws Exception {
        // Regression: with no handler for this, Spring's static-resource
        // fallback tried to serve /api/... as a file and the miss surfaced as
        // a 500. A mistyped endpoint reported the backend as broken and put a
        // stack trace in the error log for every scanner that guessed a path.
        ResponseEntity<ApiResponse<Object>> response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/api/does/not/exist", "api/does/not/exist"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError()).isNotBlank();
    }

    @Test
    @DisplayName("A 404 for an unknown path does not echo the path back")
    void unknownPathDoesNotReflectInput() throws Exception {
        // The path is attacker-controlled and the response is rendered by the
        // client, so it stays out of the body.
        ResponseEntity<ApiResponse<Object>> response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/api/<script>alert(1)</script>",
                        "api/<script>alert(1)</script>"));

        assertThat(response.getBody().getError()).doesNotContain("script");
    }

    @Test
    @DisplayName("A real endpoint called with the wrong verb is 405, not 404")
    void wrongMethodIsMethodNotAllowed() {
        // Distinct from an unknown path: the endpoint exists, so telling the
        // caller it does not would send them looking for the wrong problem.
        ResponseEntity<ApiResponse<Object>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("GET"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getError()).contains("GET");
    }

    @Test
    @DisplayName("A missing required header is the caller's mistake, so 400 not 500")
    void missingHeaderIsBadRequest() throws Exception {
        // Regression: POST /api/auth/refresh-token without an Authorization
        // header returned 500, telling the caller the server had failed.
        MethodParameter parameter = new MethodParameter(
                SampleHandler.class.getDeclaredMethod("refresh", String.class), 0);

        ResponseEntity<ApiResponse<Object>> response = handler.handleBadRequestBinding(
                new MissingRequestHeaderException("Authorization", parameter));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Naming the header is the whole value of the response.
        assertThat(response.getBody().getError()).contains("Authorization");
    }

    @Test
    @DisplayName("A missing query parameter names the parameter and returns 400")
    void missingParameterIsBadRequest() {
        ResponseEntity<ApiResponse<Object>> response = handler.handleBadRequestBinding(
                new MissingServletRequestParameterException("cycleId", "Long"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).contains("cycleId");
    }

    @Test
    @DisplayName("A malformed body is 400 and does not echo the payload back")
    void unreadableBodyIsBadRequestAndOpaque() {
        // The parser quotes the offending fragment, which is caller-supplied
        // and gets rendered by the client.
        ResponseEntity<ApiResponse<Object>> response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException(
                        "JSON parse error: <script>alert(1)</script>",
                        new MockHttpInputMessage(new byte[0])));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).doesNotContain("script");
    }

    /** Provides a real {@link MethodParameter} for the binding tests. */
    @SuppressWarnings("unused")
    private static final class SampleHandler {
        void refresh(String authHeader) {
        }
    }
}

