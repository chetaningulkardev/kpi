package com.example.kpi.config;

import com.example.kpi.entity.Role;
import com.example.kpi.entity.User;
import com.example.kpi.repository.RoleRepository;
import com.example.kpi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapAdministratorTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MockEnvironment environment = new MockEnvironment();

    @BeforeEach
    void setUp() {
        environment.setProperty("BOOTSTRAP_ADMIN_EMAIL", "admin@example.invalid");
        environment.setProperty("BOOTSTRAP_ADMIN_PASSWORD", "a-unique-test-password");
    }

    @Test
    void createsFirstAdministratorWithEncodedPassword() {
        Role role = new Role();
        role.setRoleName("ADMIN");
        when(roles.findByRoleName("ADMIN")).thenReturn(Optional.of(role));
        when(encoder.encode("a-unique-test-password")).thenReturn("encoded-password");

        BootstrapAdministrator.initialize(users, roles, encoder, environment);

        ArgumentCaptor<User> captured = ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        assertThat(captured.getValue().getEmail()).isEqualTo("admin@example.invalid");
        assertThat(captured.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(captured.getValue().getRoles()).containsExactly(role);
        assertThat(captured.getValue().getActive()).isTrue();
    }

    @Test
    void existingAdministratorPreventsAnyFurtherBootstrap() {
        when(users.existsAdministrator()).thenReturn(true);
        environment.setProperty("BOOTSTRAP_ADMIN_PASSWORD", "");

        BootstrapAdministrator.initialize(users, roles, encoder, environment);

        verify(users, never()).save(any());
        verifyNoInteractions(roles, encoder);
    }

    @Test
    void missingCredentialsFailClosed() {
        environment.setProperty("BOOTSTRAP_ADMIN_EMAIL", "");
        assertThatThrownBy(() -> BootstrapAdministrator.initialize(users, roles, encoder, environment))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL");
        verify(users, never()).save(any());
    }

    @Test
    void weakOrOverlongPasswordFailsClosed() {
        for (String password : new String[]{"short", "a".repeat(73)}) {
            environment.setProperty("BOOTSTRAP_ADMIN_PASSWORD", password);
            assertThatThrownBy(() -> BootstrapAdministrator.initialize(users, roles, encoder, environment))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
        }
        verify(users, never()).save(any());
    }

    @Test
    void existingNonAdministratorIsNeverElevated() {
        when(users.existsByEmail("admin@example.invalid")).thenReturn(true);
        assertThatThrownBy(() -> BootstrapAdministrator.initialize(users, roles, encoder, environment))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("refusing to grant privileges");
        verify(users, never()).save(any());
    }
}
