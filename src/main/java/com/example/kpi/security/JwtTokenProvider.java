package com.example.kpi.security;

import com.example.kpi.exception.InvalidTokenException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token Provider utility for creating and validating JWT tokens.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationInMs;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpirationInMs;

    /**
     * Minimum key length in bytes. {@code Keys.hmacShaKeyFor} selects the HMAC
     * variant from the key size, and anything under 64 bytes silently drops the
     * signature from HS512 to a weaker algorithm.
     */
    private static final int MIN_SECRET_BYTES = 64;

    /**
     * The placeholder that used to be committed as the default. Rejected by
     * name so an environment that still carries it fails loudly instead of
     * running on a key that is in the git history.
     */
    private static final String KNOWN_PLACEHOLDER =
            "your-secret-key-change-this-in-production-at-least-256-bits-long-random-string";

    /**
     * Fail fast rather than at the first login attempt. A missing or weak
     * signing key is a deployment error, and the application should not accept
     * traffic it cannot authenticate safely.
     */
    @PostConstruct
    void validateSigningKey() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is not set. Provide the JWT_SECRET environment variable. "
                            + "Generate one with: openssl rand -base64 64");
        }
        if (KNOWN_PLACEHOLDER.equals(jwtSecret.trim())) {
            throw new IllegalStateException(
                    "jwt.secret is still the placeholder value that was committed to source control. "
                            + "Anyone with the repository can forge tokens. Set JWT_SECRET to a new random value.");
        }
        int length = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret is too short: " + length + " bytes, need at least " + MIN_SECRET_BYTES
                            + " for HS512. Generate one with: openssl rand -base64 64");
        }
        log.info("JWT signing key accepted ({} bytes).", length);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate JWT token from UserDetails.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails.getUsername());
    }

    /**
     * Generate JWT token from email/username.
     */
    public String generateToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, email, jwtExpirationInMs);
    }

    /**
     * Generate refresh token.
     */
    public String generateRefreshToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return createToken(claims, email, refreshTokenExpirationInMs);
    }

    /**
     * Create JWT token with claims.
     */
    private String createToken(Map<String, Object> claims, String subject, long expirationTime) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Get username/email from token.
     */
    public String getUsernameFromToken(String token) {
        try {
            return ((Claims) Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parse(token)
                    .getPayload())
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Failed to get username from token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token");
        }
    }

    /**
     * Validate JWT token.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parse(token);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    public boolean isRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return "refresh".equals(claims.get("type", String.class));
    }

    /**
     * Get expiration time from token.
     */
    public Long getExpirationTimeFromToken(String token) {
        try {
            Claims claims = (Claims) Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parse(token)
                    .getPayload();
            return claims.getExpiration().getTime();
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Failed to get expiration time from token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token");
        }
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = (Claims) Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parse(token)
                    .getPayload();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException ex) {
            // Not an error worth logging: the parser throws rather than
            // returning claims, and the question being asked is exactly
            // "is this expired?".
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Failed to check token expiration: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token");
        }
    }
}
