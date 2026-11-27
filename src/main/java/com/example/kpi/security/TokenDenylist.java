package com.example.kpi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side invalidation for JWTs that have been logged out.
 *
 * <p>A JWT is self-contained: the server validates the signature and expiry and
 * asks nothing else. Logging out therefore did nothing but drop the token from
 * the browser — a token copied beforehand stayed valid for the remainder of its
 * lifetime. "Sign out" on a shared machine, or a response to a suspected theft,
 * had no effect at all.</p>
 *
 * <p>Tokens are stored as a SHA-256 hash rather than verbatim, so a dump of this
 * map does not hand out usable credentials. Each entry is kept until the token's
 * own expiry, after which the signature check rejects it anyway and the entry is
 * swept.</p>
 *
 * <p>In-memory, so denylisting applies per instance. Behind a load balancer a
 * logged-out token would still be accepted by the other instances — move this to
 * Redis before scaling out.</p>
 */
@Service
public class TokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylist.class);

    /** token hash -> the moment the token expires on its own. */
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    /**
     * Revokes a token until {@code expiresAt}.
     *
     * @param expiresAt the token's own expiry; entries are swept after this,
     *                  because past it the signature check rejects the token
     *                  regardless and keeping it would leak memory
     */
    public void revoke(String token, Instant expiresAt) {
        if (token == null || token.isBlank() || expiresAt == null) {
            return;
        }
        revoked.put(hash(token), expiresAt);
    }

    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant expiresAt = revoked.get(hash(token));
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            // Already expired on its own; drop the entry and let the normal
            // signature/expiry check handle it.
            revoked.remove(hash(token));
            return false;
        }
        return true;
    }

    public int size() {
        return revoked.size();
    }

    /**
     * Drops entries whose tokens have expired. Without this the map grows by one
     * entry per logout and never shrinks.
     */
    @Scheduled(fixedDelayString = "${app.security.denylist.sweep-ms:900000}")
    public void sweepExpired() {
        Instant now = Instant.now();
        int before = revoked.size();
        revoked.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        int removed = before - revoked.size();
        if (removed > 0) {
            log.debug("Swept {} expired token denylist entries, {} remaining", removed, revoked.size());
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK; unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

