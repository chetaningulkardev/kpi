package com.example.kpi.metrics;

import java.time.Instant;

/**
 * One observed measurement.
 *
 * <p>This is deliberately never written straight into
 * {@code Kpi.actualValue}. An external API's definition of "merged pull
 * requests" drifts, handles get mismapped, and the person being measured is the
 * one held accountable for the result — so an observation is offered as a
 * suggestion the employee accepts, not as the record itself.</p>
 *
 * @param value        the measurement
 * @param unit         what the number counts, for display
 * @param summary      a short human-readable description of what was counted
 * @param detailUrl    where a human can verify it, or {@code null}
 * @param collectedAt  when the external system was asked
 */
public record MetricValue(
        double value,
        String unit,
        String summary,
        String detailUrl,
        Instant collectedAt
) {
}

