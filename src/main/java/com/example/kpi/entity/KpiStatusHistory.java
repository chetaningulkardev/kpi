package com.example.kpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Immutable audit record of a single KPI status transition.
 *
 * <p>Written on every workflow action (submit / approve / reject / rework) so the
 * appraisal trail can answer "who changed this KPI, when, and why".</p>
 */
@Entity
@Table(name = "kpi_status_history", indexes = {
        @Index(name = "idx_kpi_status_history_kpi", columnList = "kpi_id"),
        @Index(name = "idx_kpi_status_history_changed_at", columnList = "changed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kpi_id", nullable = false)
    private Kpi kpi;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private Kpi.KpiStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private Kpi.KpiStatus toStatus;

    /** Who performed the transition. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** Rating captured at the moment of the transition, when applicable. */
    @Column(name = "rating")
    private Double rating;

    /** Reviewer / submitter note explaining the transition. */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @PrePersist
    public void prePersist() {
        if (this.changedAt == null) {
            this.changedAt = LocalDateTime.now();
        }
    }
}

