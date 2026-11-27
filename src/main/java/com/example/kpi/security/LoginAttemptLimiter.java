package com.example.kpi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window attempt limiter for the login endpoint.
 *
 * <p>Without this, {@code /api/auth/login} accepts unlimited guesses: an
 * attacker can run a password list against a known address, or a single leaked
 * password against every address, at whatever rate the server sustains.</p>
 *
 * <p>State is held in memory, so each instance limits independently. That is
 * fine for the single-instance deployment this runs in today, but behind a load
 * balancer the effective limit multiplies by the instance count — move this to
 * Redis before scaling out.</p>
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    /**
     * Entries older than the window are dead weight. Sweeping on write keeps
     * the map from growing without bound under a distributed attack, where
     * every request would otherwise add a key that nothing ever removes.
     */
    private static final int SWEEP_EVERY_N_WRITES = 256;

    private final int maxAttempts;
    private final Duration window;

    private final Map<String, Window> attempts = new ConcurrentHashMap<>();
    private final AtomicInteger writesSinceSweep = new AtomicInteger();

    public LoginAttemptLimiter(
            @Value("${app.security.login.max-attempts:10}") int maxAttempts,
            @Value("${app.security.login.window-seconds:300}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * Records an attempt and reports whether the caller has exceeded the limit.
     *
     * @param key caller identity — see {@link #keyFor(String, String)}
     * @return true when the attempt is allowed
     */
    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        maybeSweep(now);

        Window current = attempts.compute(key, (k, existing) -> {
            if (existing == null || existing.hasExpired(now, window)) {
                return new Window(now, 1);
            }
            return new Window(existing.start(), existing.count() + 1);
        });

        boolean allowed = current.count() <= maxAttempts;
        if (!allowed) {
            log.warn("Login rate limit exceeded for {} ({} attempts in {}s)", key, current.count(), window.toSeconds());
        }
        return allowed;
    }

    /**
     * Clears the counter after a successful login, so a user who mistypes a few
     * times and then succeeds is not left near the limit.
     */
    public void reset(String key) {
        attempts.remove(key);
    }

    /**
     * Seconds until the caller may try again. Drives the {@code Retry-After}
     * header.
     */
    public long retryAfterSeconds(String key) {
        Window current = attempts.get(key);
        if (current == null) {
            return 0;
        }
        long elapsed = Duration.between(current.start(), Instant.now()).toSeconds();
        return Math.max(0, window.toSeconds() - elapsed);
    }

    /**
     * Keys on client address <em>and</em> the account being targeted.
     *
     * <p>Address alone punishes everyone behind a shared NAT for one bad actor;
     * account alone lets an attacker lock a colleague out of their own login by
     * deliberately failing. Combining them limits the pair, which is what an
     * actual credential-stuffing run has to repeat.</p>
     */
    public static String keyFor(String clientAddress, String account) {
        String normalisedAccount = account == null ? "" : account.trim().toLowerCase();
        return clientAddress + "|" + normalisedAccount;
    }

    private void maybeSweep(Instant now) {
        if (writesSinceSweep.incrementAndGet() < SWEEP_EVERY_N_WRITES) {
            return;
        }
        writesSinceSweep.set(0);
        attempts.entrySet().removeIf(entry -> entry.getValue().hasExpired(now, window));
    }

    private record Window(Instant start, int count) {
        boolean hasExpired(Instant now, Duration window) {
            return start.plus(window).isBefore(now);
        }
    }
}

