package com.example.kpi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * KPI Template - Role-specific KPI templates with versioning support
 */
@Entity
@Table(name = "kpi_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String templateName;

    @Column(nullable = false)
    private String templateCode;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TemplateStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    @JsonIgnoreProperties({"users", "parentRole", "childRoles"})
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_level_id")
    private EmployeeLevel employeeLevel;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<KpiTemplateItem> templateItems = new HashSet<>();

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

    public enum TemplateStatus {
        DRAFT, ACTIVE, INACTIVE, ARCHIVED
    }

    @JsonIgnore
    public Double getTotalWeight() {
        return templateItems.stream().mapToDouble(KpiTemplateItem::getWeight).sum();
    }

    @JsonIgnore
    public boolean isWeightValid() {
        return Math.abs(getTotalWeight() - 100.0) < 0.01;
    }
}

