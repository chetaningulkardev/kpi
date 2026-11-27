package com.example.kpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Review Cycle - Represents a review period (Q1 2027, Annual Review, etc.)
 */
@Entity
@Table(name = "review_cycles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCycle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cycleName;

    @Column(nullable = false, unique = true)
    private String cycleCode;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CycleStatus status;

    @Column(nullable = false)
    private Boolean kpisGenerated;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.kpisGenerated = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum CycleStatus {
        DRAFT, OPEN, IN_PROGRESS, UNDER_REVIEW, CALIBRATION, CLOSED
    }

    /**
     * Alias method to support both naming conventions
     */
    public String getReviewCycleCode() {
        return this.cycleCode;
    }

    public void setReviewCycleCode(String code) {
        this.cycleCode = code;
    }
}

