package com.example.kpi.metrics;

/**
 * A measurement could not be collected for a reason worth surfacing — the
 * external system was unreachable, rejected our credentials, or rate-limited us.
 *
 * <p>Distinct from an empty {@link java.util.Optional}, which means the system
 * answered and had nothing to report. Conflating the two would let an outage
 * quietly present itself as a score of zero.</p>
 */
public class MetricCollectionException extends RuntimeException {

    public MetricCollectionException(String message) {
        super(message);
    }

    public MetricCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

