package com.example.kpi.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code expires_in} is defined by RFC 6749 as a count of <b>seconds</b>.
 *
 * <p>This exists because it carried milliseconds. A one-hour token advertised
 * itself as about 41 days, and the error is invisible on inspection — the
 * number looks like a plausible duration either way. Nothing consumed it yet,
 * so the fix was free; the first client to schedule a refresh from it would
 * have waited far past expiry and dropped the user mid-edit.
 */
class TokenExpiryUnitTest {

    @Test
    @DisplayName("A one-hour token reports roughly 3600, not 3600000")
    void reportsSecondsNotMilliseconds() {
        long oneHourFromNow = System.currentTimeMillis() + 3_600_000L;

        Long expiresIn = AuthenticationService.secondsUntilForTest(oneHourFromNow);

        // Tolerance covers the milliseconds spent between the two clock reads.
        assertThat(expiresIn).isBetween(3595L, 3600L);
    }

    @Test
    @DisplayName("An already-expired token reports no time left rather than a negative one")
    void neverReportsNegativeLifetime() {
        // Clock skew between issuer and reader can put expiry in the past. A
        // negative duration is a value some clients will happily treat as
        // valid; zero is unambiguous.
        long anHourAgo = System.currentTimeMillis() - 3_600_000L;

        assertThat(AuthenticationService.secondsUntilForTest(anHourAgo)).isZero();
    }

    @Test
    @DisplayName("Expiry at this instant is zero, not a rounding artefact")
    void expiryNowIsZero() {
        assertThat(AuthenticationService.secondsUntilForTest(System.currentTimeMillis()))
                .isZero();
    }
}

