package com.example.kpi.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.util.Locale;

/**
 * Manager review payload for a KPI decision.
 *
 * <p>{@code decision} drives the resulting status:</p>
 * <ul>
 *   <li>{@code APPROVE} (default) &rarr; {@code APPROVED} — requires a rating</li>
 *   <li>{@code REWORK}  &rarr; {@code REWORK_REQUIRED} — rating optional, comments required</li>
 *   <li>{@code REJECT}  &rarr; {@code REJECTED} — rating optional, comments required</li>
 * </ul>
 */
@Data
public class ManagerReviewRequest {

    /**
     * Required for APPROVE; optional for REWORK/REJECT. Bean validation only
     * enforces the range here — the "required for approve" rule lives in the
     * service so it can vary by decision.
     */
    @DecimalMin(value = "1.0", message = "rating must be at least 1.0")
    @DecimalMax(value = "5.0", message = "rating must be at most 5.0")
    private Double rating;

    private String reviewerComments;

    /** APPROVE | REWORK | REJECT. Defaults to APPROVE for backward compatibility. */
    private String decision;

    public Decision resolveDecision() {
        if (decision == null || decision.isBlank()) {
            return Decision.APPROVE;
        }
        try {
            return Decision.valueOf(decision.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "decision must be one of APPROVE, REWORK, REJECT (received: " + decision + ")");
        }
    }

    public enum Decision {
        APPROVE,
        REWORK,
        REJECT
    }
}

