package com.example.kpi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * KPI Master - Reusable KPI definitions that serve as templates for KPI usage
 */
@Entity
@Table(name = "kpi_masters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiMaster {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String kpiName;

    @Column(nullable = false, unique = true)
    private String kpiCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private KpiCategory category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private KpiType kpiType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    @Column(nullable = false)
    private String measurementUnit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "frequency_id", nullable = false)
    private com.example.kpi.entity.KpiFrequency frequency;

    // Legacy column retained in existing DB schema; kept in sync with frequency relation.
    @Column(name = "frequency", nullable = false)
    private String frequencyCodeLegacy;

    @Column(nullable = false)
    private String dataSource;

    @Column(nullable = false)
    private Double defaultWeight;

    @Column(nullable = false)
    private Boolean active;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "kpi_master_roles",
            joinColumns = @JoinColumn(name = "kpi_master_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @JsonIgnoreProperties({"users", "parentRole", "childRoles"})
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.frequency != null) {
            this.frequencyCodeLegacy = this.frequency.getFrequencyCode();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        if (this.frequency != null) {
            this.frequencyCodeLegacy = this.frequency.getFrequencyCode();
        }
        this.updatedAt = LocalDateTime.now();
    }

    public enum KpiType {
        PERCENTAGE, NUMBER, RATING, QUALITATIVE, BOOLEAN, FORMULA
    }

    public enum TargetType {
        PERCENTAGE_TARGET, NUMBER_TARGET, RATING_TARGET, QUALITATIVE_TARGET, TREND_TARGET
    }
}

