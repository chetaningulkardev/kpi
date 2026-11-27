package com.example.kpi.controller;

import com.example.kpi.auth.AuthenticationService;
import com.example.kpi.auth.UserService;
import com.example.kpi.dto.ApiResponse;
import com.example.kpi.dto.ChangePasswordRequest;
import com.example.kpi.dto.LoginRequest;
import com.example.kpi.dto.LoginResponse;
import com.example.kpi.dto.UserResponse;
import com.example.kpi.security.LoginAttemptLimiter;
import com.example.kpi.security.JwtTokenProvider;
import com.example.kpi.security.TokenDenylist;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Authentication Controller for handling login, logout, and token refresh.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenDenylist tokenDenylist;

    public AuthController(AuthenticationService authenticationService,
                          UserService userService,
                          LoginAttemptLimiter loginAttemptLimiter,
                          JwtTokenProvider jwtTokenProvider,
                          TokenDenylist tokenDenylist) {
        this.authenticationService = authenticationService;
        this.userService = userService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenDenylist = tokenDenylist;
    }

    /**
     * Login endpoint.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest,
                                                            HttpServletRequest request) {
        // Never log the password, and log the address so a stuffing run is
        // traceable after the fact.
        log.info("Login request for email: {}", loginRequest.getEmail());

        String key = LoginAttemptLimiter.keyFor(clientAddress(request), loginRequest.getEmail());
        if (!loginAttemptLimiter.tryAcquire(key)) {
            long retryAfter = loginAttemptLimiter.retryAfterSeconds(key);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter))
                    .body(ApiResponse.error(
                            "Too many sign-in attempts. Try again in " + retryAfter + " seconds.",
                            HttpStatus.TOO_MANY_REQUESTS.value()));
        }

        LoginResponse response = authenticationService.login(loginRequest);

        // Only a success clears the counter; a failure leaves it to accumulate.
        loginAttemptLimiter.reset(key);

        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    /**
     * Client address, preferring the first hop in X-Forwarded-For when the app
     * sits behind a proxy. The header is client-controlled and trivially
     * spoofed, so it is only useful when an upstream proxy is known to rewrite
     * it — it widens rate limiting, never authorisation.
     */
    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Refresh token endpoint.
     * POST /api/auth/refresh-token
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        log.info("Refresh token request");

        String refreshToken = authHeader.replace("Bearer ", "");
        LoginResponse response = authenticationService.refreshToken(refreshToken);

        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    /**
     * Get current user profile.
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        log.info("Get current user request");

        UserResponse user = userService.getCurrentUser();

        return ResponseEntity.ok(ApiResponse.success("Current user fetched successfully", user));
    }

    /**
     * Change password endpoint.
     * POST /api/auth/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        log.info("Change password request");

        userService.changePassword(request);

        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", "Password updated"));
    }

    /**
     * Validate token endpoint.
     * GET /api/auth/validate
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        log.info("Token validation request");

        String token = authHeader.replace("Bearer ", "");
        boolean isValid = authenticationService.validateToken(token);

        return ResponseEntity.ok(ApiResponse.success("Token validation result", isValid));
    }

    /**
     * Logout endpoint.
     * POST /api/auth/logout
     *
     * <p>Revokes the presented token server-side. Dropping it from the browser
     * is not enough on its own: a JWT is self-validating, so a copy taken
     * beforehand would stay usable until it expired.</p>
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Logout request");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Long expiresAtMillis = jwtTokenProvider.getExpirationTimeFromToken(token);
                if (expiresAtMillis != null) {
                    tokenDenylist.revoke(token, Instant.ofEpochMilli(expiresAtMillis));
                }
            } catch (RuntimeException ex) {
                // A malformed or already-expired token cannot be used anyway, so
                // logout still succeeds — failing here would only strand a
                // client that is trying to do the right thing.
                log.debug("Logout presented a token that could not be parsed: {}", ex.getMessage());
            }
        }

        return ResponseEntity.ok(ApiResponse.success("Logout successful", "User logged out successfully"));
    }
}
