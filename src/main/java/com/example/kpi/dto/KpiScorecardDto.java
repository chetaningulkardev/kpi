package com.example.kpi.dto;

import com.example.kpi.entity.KpiScorecard;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;

import java.time.LocalDate;
import java.util.Map;

/**
 * Flat scorecard response model for UI APIs.
 */
public record KpiScorecardDto(
        Long id,
        Long userId,
        String employeeName,
        String role,
        String employeeLevel,
        Double overallScore,
        Double maxScore,
        Double scorePercentage,
        Map<String, Double> categoryScores,
        String status,
        String calibrationNotes,
        String calibratedByName,
        String cycleCode,
        String cycleName,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate
) {
    public static KpiScorecardDto from(KpiScorecard s) {
        if (s == null) {
            return null;
        }
        User user = s.getUser();
        ReviewCycle cycle = s.getReviewCycle();
        User calibrator = s.getCalibratedBy();
        return new KpiScorecardDto(
                s.getId(),
                user != null ? user.getId() : null,
                s.getEmployeeName(),
                s.getRole(),
                s.getEmployeeLevel(),
                s.getOverallScore(),
                s.getMaxScore(),
                percentage(s.getOverallScore(), s.getMaxScore()),
                s.getCategoryScores(),
                s.getStatus() != null ? s.getStatus().name() : null,
                s.getCalibrationNotes(),
                calibrator != null ? calibrator.getFullName() : null,
                cycle != null ? cycle.getCycleCode() : null,
                cycle != null ? cycle.getCycleName() : null,
                cycle != null ? cycle.getStartDate() : null,
                cycle != null ? cycle.getEndDate() : null
        );
    }

    /**
     * Computed once here rather than in every caller. Cycles differ in how many
     * KPIs they carry, so raw scores are not comparable across them — plotting
     * a trend from {@code overallScore} would show a rise whenever someone was
     * simply assigned more work.
     *
     * <p>Null when the denominator is missing or zero: a scorecard with no
     * weighted KPIs has no percentage, and reporting one as 0% would read as
     * total failure rather than as an absence of data.
     */
    private static Double percentage(Double score, Double max) {
        if (score == null || max == null || max == 0.0) {
            return null;
        }
        return Math.round(score / max * 1000.0) / 10.0;
    }
}

