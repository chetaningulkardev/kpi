package com.example.kpi.dto;

import com.example.kpi.entity.KpiStatusHistory;
import com.example.kpi.entity.User;

import java.time.LocalDateTime;

/**
 * Read model for a single KPI status transition.
 */
public record KpiStatusHistoryDto(
        Long id,
        String fromStatus,
        String toStatus,
        Long changedById,
        String changedByName,
        LocalDateTime changedAt,
        Double rating,
        String note
) {
    public static KpiStatusHistoryDto from(KpiStatusHistory history) {
        if (history == null) {
            return null;
        }
        User actor = history.getChangedBy();
        return new KpiStatusHistoryDto(
                history.getId(),
                history.getFromStatus() != null ? history.getFromStatus().name() : null,
                history.getToStatus() != null ? history.getToStatus().name() : null,
                actor != null ? actor.getId() : null,
                actor != null ? actor.getFullName() : "System",
                history.getChangedAt(),
                history.getRating(),
                history.getNote()
        );
    }
}

