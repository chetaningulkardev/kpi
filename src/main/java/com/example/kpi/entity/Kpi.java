package com.example.kpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * KPI - Represents a KPI instance assigned to an employee for a review cycle
 * Contains snapshot of template data to maintain historical integrity
 */
@Entity
@Table(name = "kpis", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"review_cycle_id", "user_id", "kpi_master_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Kpi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "review_cycle_id", nullable = false)
    private ReviewCycle reviewCycle;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "kpi_master_id", nullable = false)
    private KpiMaster kpiMaster;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private KpiTemplate template;

    @Column(nullable = false)
    private String kpiName;

    @Column(nullable = false)
    private String kpiCode;

    @Column(nullable = false)
    private String categoryName;

    @Column(nullable = false)
    private Double weight;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false)
    private String frequency;

    @Column(columnDefinition = "TEXT")
    private String snapshotData;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private KpiStatus status;

    // Self-Assessment Data
    // No columnDefinition: a Double maps to `double precision`. Pinning it to
    // DECIMAL(10,2) silently rounded the value on every write and broke
    // Hibernate's schema validation. See migration V4.
    private Double actualValue;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(columnDefinition = "TEXT")
    private String justification;

    // ── Observed measurement ────────────────────────────────────────────────
    // Held separately from actualValue on purpose. An external system's
    // definition drifts, a handle can be mismapped, and the person measured is
    // the one held accountable — so a collected number is offered for the
    // employee to accept, never written into the record on their behalf.

    /** Last value observed from an external system, if any. */
    @Column(name = "suggested_actual_value")
    private Double suggestedActualValue;

    /** Provider that produced it, e.g. {@code GITHUB}. */
    @Column(name = "suggested_actual_source", length = 50)
    private String suggestedActualSource;

    /** Human-readable account of what was counted, and where to verify it. */
    @Column(name = "suggested_actual_summary", columnDefinition = "TEXT")
    private String suggestedActualSummary;

    @Column(name = "suggested_actual_url", columnDefinition = "TEXT")
    private String suggestedActualUrl;

    /** When the external system was asked; stale suggestions are shown as such. */
    @Column(name = "suggested_actual_at")
    private LocalDateTime suggestedActualAt;

    // Manager Review Data
    private Double rating;

    @Column(columnDefinition = "TEXT")
    private String reviewerComments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    /** Set when the employee submits the self-assessment. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Set when the reviewer approves / rejects / requests rework. */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optimistic lock. Two managers reviewing the same KPI concurrently would
     * otherwise silently overwrite each other's rating.
     */
    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // Only default the status; do not clobber a status set by the caller
        // (e.g. data seeding or migration of existing KPIs).
        if (this.status == null) {
            this.status = KpiStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum KpiStatus {
        PENDING, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, REWORK_REQUIRED
    }

    public Double getWeightedScore() {
        if (rating == null) return null;
        return (rating * weight) / 100.0;
    }
}

