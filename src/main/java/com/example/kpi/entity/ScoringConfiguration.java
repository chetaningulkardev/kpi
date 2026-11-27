package com.example.kpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Scoring Configuration - Configurable rating scales and scoring models
 */
@Entity
@Table(name = "scoring_configurations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoringConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String scaleName;

    @Column(nullable = false)
    private String scaleCode;

    @Column(nullable = false)
    private Integer minValue;

    @Column(nullable = false)
    private Integer maxValue;

    @Column(nullable = false)
    private Double defaultMaxScore;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String scaleDefinition;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

