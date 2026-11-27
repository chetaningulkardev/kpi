package com.example.kpi.metrics;

import java.time.LocalDate;
import java.util.Map;

/**
 * A request for one measurement, for one person, over one window.
 *
 * @param metricKey        what to measure, e.g. {@code pull_requests_merged}
 * @param externalHandle   the person's identity in the external system, which
 *                         is rarely their corporate email — hence
 *                         {@link com.example.kpi.entity.UserExternalIdentity}
 * @param from             start of the measurement window, inclusive
 * @param to               end of the measurement window, inclusive
 * @param config           binding-specific settings, e.g. a repository filter
 */
public record MetricQuery(
        String metricKey,
        String externalHandle,
        LocalDate from,
        LocalDate to,
        Map<String, String> config
) {

    public MetricQuery {
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    /** Config value, or {@code null} when the binding did not set one. */
    public String configValue(String key) {
        return config.get(key);
    }
}

