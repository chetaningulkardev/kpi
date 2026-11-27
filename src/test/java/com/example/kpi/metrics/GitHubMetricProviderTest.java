package com.example.kpi.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class GitHubMetricProviderTest {

    private MockRestServiceServer server;
    private GitHubMetricProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GitHubMetricProvider(true, "test-token", builder.build());
    }

    private MetricQuery query(String metricKey, Map<String, String> config) {
        return new MetricQuery(metricKey, "octocat",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), config);
    }

    /**
     * The matcher compares the raw query string, where spaces are percent-encoded.
     */
    private static String encoded(String searchExpression) {
        return searchExpression.replace(" ", "%20");
    }

    @Test
    @DisplayName("Counts merged pull requests from the search total")
    void countsMergedPullRequests() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/issues")))
                .andExpect(queryParam("q", encoded("type:pr is:merged author:octocat merged:2026-01-01..2026-03-31")))
                .andRespond(withSuccess("{\"total_count\": 17}", MediaType.APPLICATION_JSON));

        Optional<MetricValue> result = provider.fetch(query(GitHubMetricProvider.PULL_REQUESTS_MERGED, Map.of()));

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo(17.0);
        assertThat(result.get().unit()).isEqualTo("pull requests");
        server.verify();
    }

    @Test
    @DisplayName("Sends the token as a bearer credential")
    void authenticatesWithToken() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/issues")))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"total_count\": 1}", MediaType.APPLICATION_JSON));

        provider.fetch(query(GitHubMetricProvider.PULL_REQUESTS_MERGED, Map.of()));

        server.verify();
    }

    @Test
    @DisplayName("Reviews given exclude the author's own pull requests")
    void reviewsGivenExcludeSelf() {
        // commenter: would count a reply on your own PR as a review, which
        // would let anyone inflate the number without reviewing anything.
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/issues")))
                .andExpect(queryParam("q",
                        encoded("type:pr reviewed-by:octocat -author:octocat updated:2026-01-01..2026-03-31")))
                .andRespond(withSuccess("{\"total_count\": 4}", MediaType.APPLICATION_JSON));

        Optional<MetricValue> result = provider.fetch(query(GitHubMetricProvider.REVIEWS_GIVEN, Map.of()));

        assertThat(result).map(MetricValue::value).contains(4.0);
        server.verify();
    }

    @Test
    @DisplayName("A repo scope wins over an org scope")
    void repoScopeNarrowsTheSearch() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/issues")))
                .andExpect(queryParam("q",
                        encoded("type:pr is:merged author:octocat merged:2026-01-01..2026-03-31 repo:acme/widgets")))
                .andRespond(withSuccess("{\"total_count\": 2}", MediaType.APPLICATION_JSON));

        provider.fetch(query(GitHubMetricProvider.PULL_REQUESTS_MERGED,
                Map.of(GitHubMetricProvider.CONFIG_ORG, "acme",
                        GitHubMetricProvider.CONFIG_REPO, "acme/widgets")));

        server.verify();
    }

    @Test
    @DisplayName("Requests the smallest page, since only the total is needed")
    void asksForMinimalPage() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/issues")))
                .andExpect(queryParam("per_page", "1"))
                .andRespond(withSuccess("{\"total_count\": 9}", MediaType.APPLICATION_JSON));

        provider.fetch(query(GitHubMetricProvider.PULL_REQUESTS_MERGED, Map.of()));

        server.verify();
    }

    @Test
    @DisplayName("A zero count is a real answer, not an absence")
    void zeroIsReported() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/issues")))
                .andRespond(withSuccess("{\"total_count\": 0}", MediaType.APPLICATION_JSON));

        Optional<MetricValue> result = provider.fetch(query(GitHubMetricProvider.PULL_REQUESTS_MERGED, Map.of()));

        assertThat(result).isPresent();
        assertThat(result.get().value()).isZero();
    }

    @Test
    @DisplayName("An outage fails loudly rather than reporting zero")
    void serverErrorThrows() {
        // Silently returning 0 here would put a real person's score at zero
        // because GitHub had a bad minute.
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/issues")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.fetch(query(GitHubMetricProvider.PULL_REQUESTS_MERGED, Map.of())))
                .isInstanceOf(MetricCollectionException.class);
    }

    @Test
    @DisplayName("Rejected credentials fail loudly too")
    void unauthorizedThrows() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search/issues")))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> provider.fetch(query(GitHubMetricProvider.PULL_REQUESTS_MERGED, Map.of())))
                .isInstanceOf(MetricCollectionException.class);
    }

    @Test
    @DisplayName("An unknown metric key yields nothing and calls no API")
    void unknownMetricReturnsEmpty() {
        Optional<MetricValue> result = provider.fetch(query("lines_of_code_written", Map.of()));

        assertThat(result).isEmpty();
        // No expectation was set, so verify() proves nothing was requested.
        server.verify();
    }

    @Test
    @DisplayName("A blank handle yields nothing rather than searching for everyone")
    void blankHandleReturnsEmpty() {
        MetricQuery blank = new MetricQuery(GitHubMetricProvider.PULL_REQUESTS_MERGED, "  ",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), Map.of());

        assertThat(provider.fetch(blank)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Without a token the provider reports itself disabled")
    void noTokenMeansDisabled() {
        GitHubMetricProvider unconfigured =
                new GitHubMetricProvider(true, "", RestClient.builder().build());

        assertThat(unconfigured.isEnabled()).isFalse();
        assertThatThrownBy(() -> unconfigured.fetch(query(GitHubMetricProvider.PULL_REQUESTS_MERGED, Map.of())))
                .isInstanceOf(MetricCollectionException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("Disabled by flag even when a token is present")
    void flagOffMeansDisabled() {
        GitHubMetricProvider off =
                new GitHubMetricProvider(false, "a-token", RestClient.builder().build());

        assertThat(off.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Advertises exactly the metrics it can answer")
    void advertisesSupportedMetrics() {
        assertThat(provider.supportedMetrics()).containsExactlyInAnyOrder(
                GitHubMetricProvider.PULL_REQUESTS_MERGED,
                GitHubMetricProvider.REVIEWS_GIVEN,
                GitHubMetricProvider.ISSUES_CLOSED);
        assertThat(provider.providerCode()).isEqualTo("GITHUB");
    }
}

