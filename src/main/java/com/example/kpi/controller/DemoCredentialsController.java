package com.example.kpi.controller;

import com.example.kpi.config.DemoDataSeeder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/demo-credentials")
public class DemoCredentialsController {

    private final Environment environment;

    public DemoCredentialsController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping
    public DemoCredentialsResponse getDemoCredentials() {
        boolean enabled = environment.acceptsProfiles(Profiles.of("dev & !prod"));
        if (!enabled) {
            return new DemoCredentialsResponse(false, null, null);
        }
        return new DemoCredentialsResponse(
                true, DemoDataSeeder.ADMIN_EMAIL, DemoDataSeeder.DEMO_PASSWORD
        );
    }

    public record DemoCredentialsResponse(boolean enabled, String email, String password) {}
}
