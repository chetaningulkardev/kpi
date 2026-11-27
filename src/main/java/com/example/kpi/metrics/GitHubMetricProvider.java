package com.example.kpi.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Collects delivery metrics from GitHub.
 *
 * <p>Everything is answered by the Search API, which returns a
 * {@code total_count} directly. Paging through every pull request to count them
 * would be far more requests against a rate limit shared by the whole
 * installation, for a number the search index already has.</p>
 */
@Component
public class GitHubMetricProvider implements MetricProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubMetricProvider.class);

    public static final String PROVIDER_CODE = "GITHUB";

    public static final String PULL_REQUESTS_MERGED = "pull_requests_merged";
    public static final String REVIEWS_GIVEN = "reviews_given";
    public static final String ISSUES_CLOSED = "issues_closed";

    /** Restricts a search to one org or repo; set per binding. */
    public static final String CONFIG_ORG = "org";
    public static final String CONFIG_REPO = "repo";

    private static final DateTimeFormatter GH_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final boolean enabled;
    private final String token;
    private final RestClient restClient;

    // Explicit: a second constructor exists as a test seam, so Spring cannot
    // infer which one to use.
    @org.springframework.beans.factory.annotation.Autowired
    public GitHubMetricProvider(
            @Value("${app.metrics.github.enabled:false}") boolean enabled,
            @Value("${app.metrics.github.token:}") String token,
            @Value("${app.metrics.github.base-url:https://api.github.com}") String baseUrl) {

        this(enabled, token, defaultClient(baseUrl));

        if (enabled && this.token.isEmpty()) {
            // Unauthenticated search is capped at 10 requests/minute per IP,
            // which one review cycle would exhaust immediately.
            log.warn("app.metrics.github.enabled=true but no token is set; GitHub metrics are disabled.");
        }
    }

    /**
     * Test seam: lets a spec supply a client bound to a mock server.
     */
    GitHubMetricProvider(boolean enabled, String token, RestClient restClient) {
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
        this.restClient = restClient;
    }

    /**
     * Built directly rather than injected. {@code RestClient.Builder} is not an
     * auto-configured bean in this application's setup, and a provider that
     * calls exactly one host has nothing to gain from shared configuration.
     */
    private static RestClient defaultClient(String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean isEnabled() {
        return enabled && !token.isEmpty();
    }

    @Override
    public Set<String> supportedMetrics() {
        return Set.of(PULL_REQUESTS_MERGED, REVIEWS_GIVEN, ISSUES_CLOSED);
    }

    @Override
    public Optional<MetricValue> fetch(MetricQuery query) {
        if (!isEnabled()) {
            throw new MetricCollectionException("GitHub metrics are not configured.");
        }
        if (query.externalHandle() == null || query.externalHandle().isBlank()) {
            return Optional.empty();
        }

        String q = buildSearchQuery(query);
        if (q == null) {
            // Unknown metric key: the caller asked for something this provider
            // does not measure, which is not an error worth failing on.
            return Optional.empty();
        }

        long count = search(q);
        return Optional.of(new MetricValue(
                count,
                unitFor(query.metricKey()),
                describe(query, count),
                "https://github.com/search?q=" + urlEncode(q) + "&type=issues",
                Instant.now()
        ));
    }

    /**
     * Builds a GitHub search expression, or {@code null} for an unsupported
     * metric.
     */
    private String buildSearchQuery(MetricQuery query) {
        String handle = query.externalHandle().trim();
        String window = GH_DATE.format(query.from()) + ".." + GH_DATE.format(query.to());
        String scope = scopeFilter(query);

        return switch (query.metricKey()) {
            case PULL_REQUESTS_MERGED ->
                    "type:pr is:merged author:" + handle + " merged:" + window + scope;
            case REVIEWS_GIVEN ->
                    // reviewed-by excludes the author's own comments, unlike
                    // commenter:, which would count self-replies as reviews.
                    "type:pr reviewed-by:" + handle + " -author:" + handle + " updated:" + window + scope;
            case ISSUES_CLOSED ->
                    "type:issue is:closed assignee:" + handle + " closed:" + window + scope;
            default -> null;
        };
    }

    private String scopeFilter(MetricQuery query) {
        String repo = query.configValue(CONFIG_REPO);
        if (repo != null && !repo.isBlank()) {
            return " repo:" + repo.trim();
        }
        String org = query.configValue(CONFIG_ORG);
        if (org != null && !org.isBlank()) {
            return " org:" + org.trim();
        }
        // Unscoped searches span all of GitHub. That is legitimate for public
        // contributions, but a binding should normally set one.
        return "";
    }

    private long search(String q) {
        try {
            SearchResult result = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/issues")
                            .queryParam("q", q)
                            // We only want total_count, so ask for the smallest
                            // possible page rather than the default 30 items.
                            .queryParam("per_page", 1)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new MetricCollectionException(
                                "GitHub returned " + response.getStatusCode() + " for a metric search.");
                    })
                    .body(SearchResult.class);

            return result == null ? 0L : result.totalCount();

        } catch (MetricCollectionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Connection resets, timeouts and deserialisation faults all land
            // here. Reported as a failure, never as a count of zero.
            throw new MetricCollectionException("Could not reach GitHub: " + ex.getMessage(), ex);
        }
    }

    private String unitFor(String metricKey) {
        return switch (metricKey) {
            case PULL_REQUESTS_MERGED -> "pull requests";
            case REVIEWS_GIVEN -> "reviews";
            case ISSUES_CLOSED -> "issues";
            default -> "items";
        };
    }

    private String describe(MetricQuery query, long count) {
        return "%d %s for @%s between %s and %s".formatted(
                count, unitFor(query.metricKey()), query.externalHandle(),
                query.from(), query.to());
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Only the field we need. GitHub's search response is large and its shape
     * changes; binding all of it would make us fragile to additions.
     */
    private record SearchResult(
            @com.fasterxml.jackson.annotation.JsonProperty("total_count") long totalCount) {
    }

    /** Exposed for tests that need a realistic timeout budget. */
    static Duration defaultTimeout() {
        return Duration.ofSeconds(10);
    }
}

