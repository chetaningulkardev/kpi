package com.example.kpi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Locale;

/**
 * Calibration adjustment applied to a scorecard by a calibrator.
 */
@Data
public class CalibrationRequest {

    /** Optional moderated overall score; null keeps the calculated score. */
    private Double overallScore;

    /** Rationale for the adjustment — required so calibration stays auditable. */
    @NotNull(message = "notes are required for calibration")
    private String notes;

    /** Target scorecard status: CALIBRATION | FINAL | PUBLISHED. */
    private String status;

    public String resolveStatus() {
        return status == null || status.isBlank()
                ? "CALIBRATION"
                : status.trim().toUpperCase(Locale.ROOT);
    }
}

