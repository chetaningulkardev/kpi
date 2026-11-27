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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

@SpringBootTest(properties = "app.cors.allowed-origins=https://allowed.example.invalid")
class CorsPolicyTest {
    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void configuredOriginIsAcceptedForAuthentication() throws Exception {
        var response = mvc.perform(options("/api/auth/login")
                .header("Origin", "https://allowed.example.invalid")
                .header("Access-Control-Request-Method", "POST")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://allowed.example.invalid");
    }

    @Test
    void unconfiguredOriginIsRejectedForKpiEndpoints() throws Exception {
        var response = mvc.perform(options("/api/kpi")
                .header("Origin", "https://untrusted.example.invalid")
                .header("Access-Control-Request-Method", "GET")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }
}
