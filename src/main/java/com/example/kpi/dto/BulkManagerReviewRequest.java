package com.example.kpi.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Payload for reviewing several KPIs in one call (bulk approve / reject / rework).
 */
@Data
public class BulkManagerReviewRequest {

    @NotEmpty(message = "kpiIds must not be empty")
    private List<Long> kpiIds;

    /** Applied to every KPI in {@link #kpiIds}. */
    private ManagerReviewRequest review = new ManagerReviewRequest();
}

