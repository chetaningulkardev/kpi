package com.example.kpi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KPI Template Item - Links KPI Masters to Templates with level-specific weights
 */
@Entity
@Table(name = "kpi_template_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiTemplateItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    @JsonIgnore
    private KpiTemplate template;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "kpi_master_id", nullable = false)
    @JsonIgnoreProperties({"roles", "category", "frequency"})
    private KpiMaster kpiMaster;

    @Column(nullable = false)
    private Double weight;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_level_id")
    private EmployeeLevel employeeLevel;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false)
    private Integer sequenceOrder;

    @Column(nullable = false)
    private Boolean active;
}

