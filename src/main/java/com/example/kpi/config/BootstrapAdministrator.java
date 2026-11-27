package com.example.kpi.config;

import com.example.kpi.entity.Role;
import com.example.kpi.entity.User;
import com.example.kpi.repository.RoleRepository;
import com.example.kpi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

final class BootstrapAdministrator {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdministrator.class);

    private BootstrapAdministrator() {
    }

    static void initialize(UserRepository users, RoleRepository roles, PasswordEncoder encoder, Environment environment) {
        if (users.existsAdministrator()) {
            return;
        }

        String email = environment.getProperty("BOOTSTRAP_ADMIN_EMAIL", "").trim();
        String password = environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD", "");
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD to create the first administrator");
        }
        if (password.length() < 12 || passwordBytes > 72) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters and at most 72 UTF-8 bytes");
        }
        if (users.existsByEmail(email)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL belongs to an existing non-administrator; refusing to grant privileges");
        }

        Role adminRole = roles.findByRoleName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));
        User admin = new User();
        admin.setEmployeeCode("BOOTSTRAP-" + UUID.randomUUID());
        admin.setFirstName("System");
        admin.setLastName("Administrator");
        admin.setEmail(email);
        admin.setPassword(encoder.encode(password));
        admin.setActive(true);
        admin.setRoles(Set.of(adminRole));
        users.save(admin);
        log.info("Created initial administrator: {}", email);
    }
}
