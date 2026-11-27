package com.example.kpi.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TokenDenylistTest {

    private static final String TOKEN = "eyJhbGciOiJIUzUxMiJ9.payload.signature";

    private Instant inAnHour() {
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }

    @Test
    @DisplayName("An unrevoked token is accepted")
    void unknownTokenIsNotRevoked() {
        assertThat(new TokenDenylist().isRevoked(TOKEN)).isFalse();
    }

    @Test
    @DisplayName("A revoked token is rejected for the rest of its lifetime")
    void revokedTokenIsRejected() {
        TokenDenylist denylist = new TokenDenylist();
        denylist.revoke(TOKEN, inAnHour());

        // The point of the denylist: this token is still cryptographically
        // valid and unexpired, so nothing else would stop it.
        assertThat(denylist.isRevoked(TOKEN)).isTrue();
    }

    @Test
    @DisplayName("Revoking one token does not affect another")
    void revocationIsPerToken() {
        TokenDenylist denylist = new TokenDenylist();
        denylist.revoke(TOKEN, inAnHour());

        assertThat(denylist.isRevoked("a.different.token")).isFalse();
    }

    @Test
    @DisplayName("An entry past its own expiry stops being consulted")
    void expiredEntryIsIgnored() {
        TokenDenylist denylist = new TokenDenylist();
        denylist.revoke(TOKEN, Instant.now().minusSeconds(1));

        // Past expiry the signature check rejects it anyway, so the entry is
        // dead weight and is dropped on read.
        assertThat(denylist.isRevoked(TOKEN)).isFalse();
        assertThat(denylist.size()).isZero();
    }

    @Test
    @DisplayName("The sweep removes expired entries so the map cannot grow forever")
    void sweepRemovesExpiredEntries() {
        TokenDenylist denylist = new TokenDenylist();
        denylist.revoke("expired.token.one", Instant.now().minusSeconds(60));
        denylist.revoke("expired.token.two", Instant.now().minusSeconds(30));
        denylist.revoke(TOKEN, inAnHour());
        assertThat(denylist.size()).isEqualTo(3);

        denylist.sweepExpired();

        assertThat(denylist.size())
                .as("only the still-live token should remain")
                .isEqualTo(1);
        assertThat(denylist.isRevoked(TOKEN)).isTrue();
    }

    @Test
    @DisplayName("Null and blank input is tolerated")
    void nullAndBlankAreSafe() {
        TokenDenylist denylist = new TokenDenylist();

        denylist.revoke(null, inAnHour());
        denylist.revoke("   ", inAnHour());
        denylist.revoke(TOKEN, null);

        assertThat(denylist.size()).isZero();
        assertThat(denylist.isRevoked(null)).isFalse();
        assertThat(denylist.isRevoked("")).isFalse();
    }

    @Test
    @DisplayName("Revoking the same token twice does not create a second entry")
    void revokeIsIdempotent() {
        TokenDenylist denylist = new TokenDenylist();
        denylist.revoke(TOKEN, inAnHour());
        denylist.revoke(TOKEN, inAnHour());

        assertThat(denylist.size()).isEqualTo(1);
    }
}

