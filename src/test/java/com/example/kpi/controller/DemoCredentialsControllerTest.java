package com.example.kpi.controller;

import com.example.kpi.config.DemoDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoCredentialsControllerTest {

    @Test
    void exposesSeededCredentialsInDevProfile() {
        DemoCredentialsController controller = new DemoCredentialsController(
                new MockEnvironment().withProperty("spring.profiles.active", "dev")
        );

        DemoCredentialsController.DemoCredentialsResponse response = controller.getDemoCredentials();

        assertTrue(response.enabled());
        assertEquals(DemoDataSeeder.ADMIN_EMAIL, response.email());
        assertEquals(DemoDataSeeder.DEMO_PASSWORD, response.password());
    }

    @Test
    void hidesSeededCredentialsOutsideDevProfile() {
        DemoCredentialsController controller = new DemoCredentialsController(
                new MockEnvironment().withProperty("spring.profiles.active", "prod")
        );

        DemoCredentialsController.DemoCredentialsResponse response = controller.getDemoCredentials();

        assertFalse(response.enabled());
        assertNull(response.email());
        assertNull(response.password());
    }

    @Test
    void disablesSeededCredentialsWhenDevAndProdAreBothActive() {
        DemoCredentialsController controller = new DemoCredentialsController(
                new MockEnvironment().withProperty("spring.profiles.active", "dev,prod")
        );

        assertFalse(controller.getDemoCredentials().enabled());
    }
}
