package com.example.kpi.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Pins the response headers that keep user-supplied content from executing as
 * part of this application.
 *
 * <p>These are easy to remove by accident — they live in one lambda far from
 * the code they protect, and nothing in the UI breaks when they go missing.
 * The damage only shows up when someone uploads the wrong file.
 *
 * <p>MockMvc is assembled by hand rather than through {@code @AutoConfigureMockMvc}:
 * Boot 4 no longer ships that annotation in {@code spring-boot-test-autoconfigure}.
 * {@code springSecurity()} is what puts the real filter chain in front of the
 * request, which is the entire subject of this test.
 */
@SpringBootTest
class HttpSecurityHeadersTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** A public endpoint, so the assertions are about headers and not auth. */
    private MvcResult anyResponse() throws Exception {
        return mockMvc.perform(get("/actuator/health")).andReturn();
    }

    private String csp() throws Exception {
        return anyResponse().getResponse().getHeader("Content-Security-Policy");
    }

    @Test
    void sendsAContentSecurityPolicy() throws Exception {
        assertThat(csp()).isNotNull();
        assertThat(csp()).contains("default-src 'self'");
    }

    @Test
    void doesNotAllowInlineScript() throws Exception {
        // The whole point of the policy. 'unsafe-inline' in script-src would
        // readmit precisely the injection it exists to prevent, and it is the
        // usual thing added to make some third-party widget work.
        assertThat(csp()).doesNotContain("script-src 'self' 'unsafe-inline'");
        assertThat(csp()).contains("script-src 'self'");
    }

    @Test
    void forbidsObjectEmbeddingAndFraming() throws Exception {
        assertThat(csp()).contains("object-src 'none'");
        assertThat(csp()).contains("frame-ancestors 'none'");
    }

    @Test
    void deniesFramingForOlderBrowsers() throws Exception {
        assertThat(anyResponse().getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void sendsAReferrerPolicy() throws Exception {
        assertThat(anyResponse().getResponse().getHeader("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void tellsBrowsersNotToSniffContentTypes() throws Exception {
        // Spring sets this by default; asserted so that a future call to
        // .headers(HeadersConfigurer::disable) cannot remove it unnoticed.
        assertThat(anyResponse().getResponse().getHeader("X-Content-Type-Options"))
                .isEqualTo("nosniff");
    }
}

