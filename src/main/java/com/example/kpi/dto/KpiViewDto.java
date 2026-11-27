package com.example.kpi.dto;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;

/**
 * Flat KPI response model for UI APIs.
 * Avoids recursive entity serialization and hides sensitive user fields.
 */
public record KpiViewDto(
        Long id,
        String kpiName,
        String kpiCode,
        String categoryName,
        Double weight,
        String target,
        String frequency,
        String status,
        Double actualValue,
        String comments,
        String justification,
        Double rating,
        String reviewerComments,
        UserRef user,
        UserRef reviewer,
        ReviewCycleRef reviewCycle,
        String snapshotData,

        // Observed from an external system and offered to the employee. Kept
        // distinct from actualValue so the UI can show "suggested" rather than
        // presenting a collected number as something the person entered.
        Double suggestedActualValue,
        String suggestedActualSource,
        String suggestedActualSummary,
        String suggestedActualUrl,
        String suggestedActualAt
) {
    public record UserRef(Long id, String firstName, String lastName, String email) {}
    public record ReviewCycleRef(Long id, String cycleCode, String cycleName) {}

    public static KpiViewDto from(Kpi kpi) {
        if (kpi == null) {
            return null;
        }
        return new KpiViewDto(
                kpi.getId(),
                kpi.getKpiName(),
                kpi.getKpiCode(),
                kpi.getCategoryName(),
                kpi.getWeight(),
                kpi.getTarget(),
                kpi.getFrequency(),
                kpi.getStatus() != null ? kpi.getStatus().name() : null,
                kpi.getActualValue(),
                kpi.getComments(),
                kpi.getJustification(),
                kpi.getRating(),
                kpi.getReviewerComments(),
                toUserRef(kpi.getUser()),
                toUserRef(kpi.getReviewer()),
                toCycleRef(kpi.getReviewCycle()),
                kpi.getSnapshotData(),
                kpi.getSuggestedActualValue(),
                kpi.getSuggestedActualSource(),
                kpi.getSuggestedActualSummary(),
                kpi.getSuggestedActualUrl(),
                kpi.getSuggestedActualAt() != null ? kpi.getSuggestedActualAt().toString() : null
        );
    }

    private static UserRef toUserRef(User user) {
        if (user == null) {
            return null;
        }
        return new UserRef(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
    }

    private static ReviewCycleRef toCycleRef(ReviewCycle cycle) {
        if (cycle == null) {
            return null;
        }
        return new ReviewCycleRef(cycle.getId(), cycle.getCycleCode(), cycle.getCycleName());
    }
}

