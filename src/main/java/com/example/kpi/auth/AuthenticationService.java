package com.example.kpi.auth;

import com.example.kpi.dto.LoginRequest;
import com.example.kpi.dto.LoginResponse;
import com.example.kpi.entity.User;
import com.example.kpi.exception.InvalidCredentialsException;
import com.example.kpi.exception.InvalidTokenException;
import com.example.kpi.mapper.UserMapper;
import com.example.kpi.repository.UserRepository;
import com.example.kpi.security.JwtTokenProvider;
import com.example.kpi.security.TokenDenylist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Authentication Service for handling user login, logout, and token management.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final TokenDenylist tokenDenylist;

    public AuthenticationService(AuthenticationManager authenticationManager,
                                JwtTokenProvider tokenProvider,
                                UserRepository userRepository,
                                UserMapper userMapper,
                                TokenDenylist tokenDenylist) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.tokenDenylist = tokenDenylist;
    }

    /**
     * Authenticate user and generate JWT token.
     */
    public LoginResponse login(LoginRequest loginRequest) {
        log.info("Login attempt for email: {}", loginRequest.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            User user = (User) authentication.getPrincipal();

            // Update last login
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            // Generate tokens
            String accessToken = tokenProvider.generateToken(user);
            String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());
            Long expiresIn = secondsUntil(tokenProvider.getExpirationTimeFromToken(accessToken));

            log.info("User logged in successfully: {}", user.getEmail());

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(expiresIn)
                    .user(userMapper.toResponse(user))
                    .build();

        } catch (AuthenticationException ex) {
            log.warn("Authentication failed for email: {}", loginRequest.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }

    /**
     * Validate JWT token.
     */
    public boolean validateToken(String token) {
        try {
            return tokenProvider.validateToken(token) && !tokenProvider.isRefreshToken(token)
                    && !tokenDenylist.isRevoked(token);
        } catch (Exception ex) {
            log.warn("Token validation failed: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid or expired token");
        }
    }

    /**
     * Refresh access token.
     */
    public LoginResponse refreshToken(String refreshToken) {
        log.info("Refreshing token");

        if (!tokenProvider.validateToken(refreshToken) || !tokenProvider.isRefreshToken(refreshToken)
                || tokenDenylist.isRevoked(refreshToken)) {
            log.warn("Invalid refresh token");
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        String email = tokenProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidTokenException("User not found"));
        if (!user.isEnabled()) {
            throw new InvalidTokenException("User account is disabled");
        }

        String newAccessToken = tokenProvider.generateToken(user);
        String newRefreshToken = tokenProvider.generateRefreshToken(email);
        Long expiresIn = secondsUntil(tokenProvider.getExpirationTimeFromToken(newAccessToken));

        log.info("Token refreshed successfully for user: {}", email);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .user(userMapper.toResponse(user))
                .build();
    }

    /**
     * Remaining token lifetime in **seconds**.
     * <p>
     * The wire field is {@code expires_in}, which RFC 6749 defines as seconds.
     * This returned milliseconds, so a one-hour token advertised itself as
     * roughly 41 days. Nothing consumed it yet, which is the only reason it
     * had not caused a problem: the first client to schedule a refresh from it
     * would have waited well past expiry and logged the user out mid-edit.
     * <p>
     * Floored at zero rather than allowed to go negative — a clock skew that
     * makes a just-issued token look already expired is better reported as
     * "no time left" than as a negative duration a client may treat as valid.
     */
    private static Long secondsUntil(long expiryEpochMillis) {
        long remainingMillis = expiryEpochMillis - System.currentTimeMillis();
        return Math.max(0L, remainingMillis / 1000L);
    }

    /** Test seam for {@link #secondsUntil(long)}, which is otherwise unreachable. */
    static Long secondsUntilForTest(long expiryEpochMillis) {
        return secondsUntil(expiryEpochMillis);
    }

    /**
     * Get JWT expiration time.
     */
    public Long getTokenExpirationTime(String token) {
        return tokenProvider.getExpirationTimeFromToken(token);
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        return tokenProvider.isTokenExpired(token);
    }
}
