package com.example.kpi.auth;

import com.example.kpi.entity.User;
import com.example.kpi.exception.InvalidTokenException;
import com.example.kpi.mapper.UserMapper;
import com.example.kpi.repository.UserRepository;
import com.example.kpi.security.JwtTokenProvider;
import com.example.kpi.security.TokenDenylist;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RefreshTokenSecurityTest {
    private final JwtTokenProvider provider = new JwtTokenProvider();
    private final TokenDenylist denylist = new TokenDenylist();
    private final UserRepository users = mock(UserRepository.class);
    private final AuthenticationService service = new AuthenticationService(
            mock(AuthenticationManager.class), provider, users, mock(UserMapper.class), denylist);

    RefreshTokenSecurityTest() {
        ReflectionTestUtils.setField(provider, "jwtSecret",
                "test-only-signing-key-not-used-outside-the-test-suite-0123456789-abcdefghijklmnop");
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 3600000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpirationInMs", 86400000L);
    }

    @Test
    void accessTokensCannotBeUsedToRefresh() {
        assertThatThrownBy(() -> service.refreshToken(provider.generateToken("test@example.invalid")))
                .isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(users);
    }

    @Test
    void revokedRefreshTokensCannotBeUsed() {
        String refresh = provider.generateRefreshToken("test@example.invalid");
        denylist.revoke(refresh, Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> service.refreshToken(refresh)).isInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(users);
    }

    @Test
    void disabledUsersCannotRefresh() {
        User user = new User();
        user.setActive(false);
        when(users.findByEmail("test@example.invalid")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.refreshToken(provider.generateRefreshToken("test@example.invalid")))
                .isInstanceOf(InvalidTokenException.class);
    }
}
