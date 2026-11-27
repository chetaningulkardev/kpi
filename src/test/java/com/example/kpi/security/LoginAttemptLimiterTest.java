package com.example.kpi.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    private LoginAttemptLimiter limiter(int maxAttempts, long windowSeconds) {
        return new LoginAttemptLimiter(maxAttempts, windowSeconds);
    }

    @Test
    @DisplayName("Allows attempts up to the limit, then blocks")
    void blocksAfterLimit() {
        LoginAttemptLimiter limiter = limiter(3, 300);
        String key = LoginAttemptLimiter.keyFor("10.0.0.1", "user@example.invalid");

        assertThat(limiter.tryAcquire(key)).isTrue();
        assertThat(limiter.tryAcquire(key)).isTrue();
        assertThat(limiter.tryAcquire(key)).isTrue();

        assertThat(limiter.tryAcquire(key))
                .as("the fourth attempt exceeds a limit of three")
                .isFalse();
    }

    @Test
    @DisplayName("A successful login clears the counter")
    void resetClearsCounter() {
        LoginAttemptLimiter limiter = limiter(2, 300);
        String key = LoginAttemptLimiter.keyFor("10.0.0.1", "user@example.invalid");

        limiter.tryAcquire(key);
        limiter.tryAcquire(key);
        assertThat(limiter.tryAcquire(key)).isFalse();

        // Someone who mistypes twice and then gets it right should not be left
        // one attempt away from being locked out.
        limiter.reset(key);
        assertThat(limiter.tryAcquire(key)).isTrue();
    }

    @Test
    @DisplayName("The window expires, letting a genuine user back in")
    void windowExpires() throws InterruptedException {
        LoginAttemptLimiter limiter = limiter(1, 1);
        String key = LoginAttemptLimiter.keyFor("10.0.0.1", "user@example.invalid");

        assertThat(limiter.tryAcquire(key)).isTrue();
        assertThat(limiter.tryAcquire(key)).isFalse();

        Thread.sleep(1_100);

        assertThat(limiter.tryAcquire(key))
                .as("a one-second window must not lock anyone out permanently")
                .isTrue();
    }

    @Test
    @DisplayName("One attacker does not lock out other accounts from the same address")
    void differentAccountsAreCountedSeparately() {
        LoginAttemptLimiter limiter = limiter(2, 300);
        String victim = LoginAttemptLimiter.keyFor("10.0.0.1", "victim@example.invalid");
        String other = LoginAttemptLimiter.keyFor("10.0.0.1", "colleague@example.invalid");

        limiter.tryAcquire(victim);
        limiter.tryAcquire(victim);
        assertThat(limiter.tryAcquire(victim)).isFalse();

        // Shared office NAT: everyone would be behind one address.
        assertThat(limiter.tryAcquire(other)).isTrue();
    }

    @Test
    @DisplayName("One account is limited per address, so an attacker cannot lock a user out globally")
    void sameAccountFromDifferentAddressesIsSeparate() {
        LoginAttemptLimiter limiter = limiter(2, 300);
        String attacker = LoginAttemptLimiter.keyFor("203.0.113.9", "victim@example.invalid");
        String legitimate = LoginAttemptLimiter.keyFor("10.0.0.1", "victim@example.invalid");

        limiter.tryAcquire(attacker);
        limiter.tryAcquire(attacker);
        assertThat(limiter.tryAcquire(attacker)).isFalse();

        // The real user, at their own address, is unaffected — deliberately
        // failing someone else's login must not deny them service.
        assertThat(limiter.tryAcquire(legitimate)).isTrue();
    }

    @Test
    @DisplayName("Account matching ignores case and surrounding whitespace")
    void keyIsNormalised() {
        assertThat(LoginAttemptLimiter.keyFor("10.0.0.1", "  User@Example.Invalid "))
                .isEqualTo(LoginAttemptLimiter.keyFor("10.0.0.1", "user@example.invalid"));
    }

    @Test
    @DisplayName("A null account does not blow up key construction")
    void nullAccountIsTolerated() {
        assertThat(LoginAttemptLimiter.keyFor("10.0.0.1", null)).isEqualTo("10.0.0.1|");
    }

    @Test
    @DisplayName("Retry-After counts down and is zero for an unknown key")
    void retryAfterReflectsRemainingWindow() {
        LoginAttemptLimiter limiter = limiter(1, 300);
        String key = LoginAttemptLimiter.keyFor("10.0.0.1", "user@example.invalid");

        assertThat(limiter.retryAfterSeconds(key)).isZero();

        limiter.tryAcquire(key);
        assertThat(limiter.retryAfterSeconds(key)).isBetween(1L, 300L);
    }
}
