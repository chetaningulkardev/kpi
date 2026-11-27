package com.example.kpi.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtTokenPurposeTest {
    private final JwtTokenProvider provider = new JwtTokenProvider();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(provider, "jwtSecret",
                "test-only-signing-key-not-used-outside-the-test-suite-0123456789-abcdefghijklmnop");
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 3600000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpirationInMs", 86400000L);
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshTokensCannotAuthenticateApiRequests() throws Exception {
        UserDetailsService users = mock(UserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, users, new TokenDenylist());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("Authorization", "Bearer " + provider.generateRefreshToken("test@example.invalid"));
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(users);
    }

    @Test
    void accessTokensStillAuthenticateApiRequests() throws Exception {
        UserDetailsService users = mock(UserDetailsService.class);
        var user = User.withUsername("test@example.invalid").password("unused").roles("ADMIN").build();
        when(users.loadUserByUsername(user.getUsername())).thenReturn(user);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, users, new TokenDenylist());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("Authorization", "Bearer " + provider.generateToken(user));
        try {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(user.getUsername());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
