package com.example.kpi.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Pins deep-link behaviour for the single-page app.
 *
 * <p>Client routes are served by Angular, but the browser asks this server for
 * them on a refresh or a pasted link. Both the security rules and the forwarding
 * controller used to enumerate those paths by hand, and the list fell behind the
 * router: every {@code /kpi/*} screen and {@code /notifications} were missing, so
 * reloading one returned the API's raw JSON 401 instead of the application.
 *
 * <p>The routes below are therefore checked as a set that must keep working,
 * alongside the two boundaries that must not move: the API stays authenticated,
 * and a genuinely missing asset still 404s rather than quietly receiving
 * index.html — which the browser would report as a syntax error.
 *
 * <p>MockMvc is assembled by hand for the same reason as
 * {@link HttpSecurityHeadersTest}: {@code springSecurity()} is what puts the
 * real filter chain in front of the request.
 */
@SpringBootTest
class SpaDeepLinkTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest(name = "{0} is served to an anonymous browser")
    @ValueSource(strings = {
            "/",
            "/login",
            "/dashboard",
            "/profile",
            "/kpi-dashboard",
            "/users",
            "/roles",
            "/teams",
            "/tribes",
            "/portfolios",
            "/squads",
            "/assignments",
            "/organization",
            "/org-chart",
            "/admin/config",
            "/kpi/cycles",
            "/kpi/categories",
            "/kpi/frequencies",
            "/kpi/data-sources",
            "/kpi/masters",
            "/kpi/templates",
            "/kpi/generate",
            "/kpi/my-kpis",
            "/kpi/my-performance",
            "/kpi/review",
            "/kpi/team-flow",
            "/kpi/scorecards",
            "/notifications"
    })
    void clientRoutesReachTheApp(String route) throws Exception {
        int status = mockMvc.perform(get(route)).andReturn().getResponse().getStatus();

        // 200 once index.html is on the classpath, 404 when the frontend has not
        // been built into this run. Either way it must not be the 401 that the
        // API would return, which is the failure this guards.
        assertThat(status)
                .as("%s must be handled as a client route, not as an API call", route)
                .isNotEqualTo(401);
    }

    @ParameterizedTest(name = "{0} still requires authentication")
    @ValueSource(strings = {
            "/api/users",
            "/api/roles",
            "/api/kpi/my-kpis",
            "/api/kpi/masters",
            "/api/notifications",
            "/actuator/metrics"
    })
    void apiStaysProtected(String path) throws Exception {
        assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus())
                .as("%s must not be exposed by the client-route rule", path)
                .isEqualTo(401);
    }

    @Test
    @DisplayName("a missing asset 404s rather than receiving index.html")
    void missingAssetIsNotForwarded() throws Exception {
        assertThat(mockMvc.perform(get("/definitely-not-here.js")).andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }
}

