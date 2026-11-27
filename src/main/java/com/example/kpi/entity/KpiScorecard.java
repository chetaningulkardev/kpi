package com.example.kpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * KPI Scorecard - Auto-generated scorecard containing all KPI reviews and scores for an employee per cycle
 */
@Entity
@Table(name = "kpi_scorecards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiScorecard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "review_cycle_id", nullable = false)
    private ReviewCycle reviewCycle;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private KpiTemplate template;

    @Column(nullable = false)
    private String employeeName;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String employeeLevel;

    // Scores are Double, so they map to `double precision`. They were
    // previously pinned to DECIMAL(5,2), which rounded a computed score such
    // as 4.3333 down to 4.33 on save. See migration V4.
    private Double overallScore;

    private Double maxScore;

    @ElementCollection
    @CollectionTable(name = "scorecard_category_scores", joinColumns = @JoinColumn(name = "scorecard_id"))
    @MapKeyColumn(name = "category_name")
    @Column(name = "category_score")
    @Builder.Default
    private Map<String, Double> categoryScores = new HashMap<>();

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ScorecardStatus status;

    @Column(columnDefinition = "TEXT")
    private String calibrationNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calibrator_id")
    private User calibratedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // Default only — never clobber a status/score supplied by the caller.
        if (this.status == null) {
            this.status = ScorecardStatus.DRAFT;
        }
        if (this.maxScore == null) {
            this.maxScore = 5.0;
        }
        if (this.employeeLevel == null) {
            this.employeeLevel = "";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum ScorecardStatus {
        DRAFT, UNDER_REVIEW, CALIBRATION, FINAL, PUBLISHED
    }
}

