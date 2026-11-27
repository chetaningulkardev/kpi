package com.example.kpi.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The health endpoint doubles as the container readiness probe, so what it
 * reports decides whether an instance receives traffic at all.
 *
 * <p>This exists because it did not hold: a mail health indicator was opening
 * an SMTP connection on every probe and reporting DOWN when no relay answered.
 * The application was serving requests perfectly and would have been kept out
 * of the load balancer because notification email was unavailable.
 */
@SpringBootTest
class HealthEndpointTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void reportsUpWithNoMailRelayReachable() throws Exception {
        String body = mockMvc.perform(get("/actuator/health"))
                .andReturn().getResponse().getContentAsString();

        // No SMTP server runs during tests, which is precisely the condition
        // that used to force this to DOWN.
        assertThat(body).contains("\"status\":\"UP\"");
    }

    @Test
    void staysReachableWithoutAuthentication() throws Exception {
        // A probe carries no credentials. If this ever needs a token, every
        // instance fails its readiness check on startup.
        int status = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }
}

