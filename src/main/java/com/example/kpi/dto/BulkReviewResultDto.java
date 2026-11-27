package com.example.kpi.dto;

import java.util.List;

/**
 * Outcome of a bulk review call — per-KPI failures do not abort the whole batch.
 */
public record BulkReviewResultDto(
        int requested,
        int succeeded,
        int failed,
        List<Failure> failures
) {
    public record Failure(Long kpiId, String reason) {}
}

