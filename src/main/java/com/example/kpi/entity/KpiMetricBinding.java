package com.example.kpi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Says how one KPI is measured automatically.
 *
 * <p>{@code KpiMaster.dataSource} is free text — "GitHub", "Jira", "Manual" —
 * which describes intent to a human but tells the application nothing it can
 * act on. This binding is the executable version: which provider, which metric,
 * and any scope it needs.</p>
 *
 * <p>Absence of a binding means the KPI is measured by hand, which stays the
 * default.</p>
 */
@Entity
@Table(
        name = "kpi_metric_bindings",
        uniqueConstraints = {
                // A KPI with two bindings would have two competing "actuals"
                // and no rule for choosing between them.
                @UniqueConstraint(
                        name = "uk_kpi_metric_binding_master",
                        columnNames = {"kpi_master_id"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiMetricBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "kpi_master_id", nullable = false)
    private KpiMaster kpiMaster;

    /** Matches {@link com.example.kpi.metrics.MetricProvider#providerCode()}. */
    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;

    /** Matches one of the provider's supported metric keys. */
    @Column(name = "metric_key", nullable = false, length = 100)
    private String metricKey;

    /**
     * Provider-specific scope as JSON, e.g. {@code {"org":"acme"}}. Stored as
     * text rather than modelled: every provider needs different keys, and a
     * column per provider would grow without bound.
     */
    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

