package com.example.kpi.metrics;

import java.util.Optional;
import java.util.Set;

/**
 * A source of observed measurements — GitHub, Jira, SonarQube, a CI system.
 *
 * <p>Implementations are discovered by Spring, so adding a source means adding
 * a bean and nothing else. {@link MetricCollectionService} resolves a provider
 * by {@link #providerCode()} and never names one directly.</p>
 */
public interface MetricProvider {

    /**
     * Stable identifier, stored on bindings and identities. Changing it orphans
     * existing rows, so treat it as part of the schema.
     */
    String providerCode();

    /**
     * Whether this provider is configured well enough to be called. A provider
     * with no credentials reports {@code false} rather than failing at call
     * time, so the UI can explain the situation instead of showing an error.
     */
    boolean isEnabled();

    /** Metric keys this provider understands. */
    Set<String> supportedMetrics();

    /**
     * Fetches one measurement.
     *
     * <p>Returns empty when the metric genuinely cannot be determined — an
     * unknown metric key, or an external system that has no record of the
     * handle. Implementations throw {@link MetricCollectionException} for
     * transport and authentication problems, which are worth reporting to the
     * caller as a failure rather than as "no data".</p>
     */
    Optional<MetricValue> fetch(MetricQuery query);
}

