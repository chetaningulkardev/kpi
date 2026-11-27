package com.example.kpi.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * Employee self-assessment payload for KPI submission.
 */
@Data
public class SelfAssessmentRequest {

    /**
     * Required at submission time (enforced in the service, which knows whether
     * the KPI already carries a value from an earlier draft).
     */
    @PositiveOrZero(message = "actualValue cannot be negative")
    private Double actualValue;

    private String comments;

    private String justification;
}

