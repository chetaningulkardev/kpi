package com.example.kpi.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The signing key is what stands between an attacker and a token for any
 * account, so a bad one must stop the application rather than surface at the
 * first login.
 */
class JwtTokenProviderSecretValidationTest {

    private static final String VALID_SECRET =
            "a-sufficiently-long-random-signing-key-for-hs512-0123456789-abcdefghij";

    private JwtTokenProvider providerWithSecret(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        return provider;
    }

    @Test
    @DisplayName("A missing secret stops startup")
    void blankSecretIsRejected() {
        assertThatThrownBy(() -> providerWithSecret("").validateSigningKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");

        assertThatThrownBy(() -> providerWithSecret(null).validateSigningKey())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("The placeholder that was committed to source control is rejected by name")
    void committedPlaceholderIsRejected() {
        // Anyone with the repository knows this value and could mint tokens.
        String placeholder =
                "your-secret-key-change-this-in-production-at-least-256-bits-long-random-string";

        assertThatThrownBy(() -> providerWithSecret(placeholder).validateSigningKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    @DisplayName("A key too short for HS512 is rejected")
    void shortSecretIsRejected() {
        // 32 bytes is fine for HS256 but silently weakens HS512.
        assertThatThrownBy(() -> providerWithSecret("0123456789abcdef0123456789abcdef").validateSigningKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    @DisplayName("A long random key is accepted")
    void validSecretIsAccepted() {
        assertThat(VALID_SECRET.length()).isGreaterThanOrEqualTo(64);
        assertThatCode(() -> providerWithSecret(VALID_SECRET).validateSigningKey())
                .doesNotThrowAnyException();
    }
}

