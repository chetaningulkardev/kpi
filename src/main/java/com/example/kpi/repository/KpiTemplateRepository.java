package com.example.kpi.repository;

import com.example.kpi.entity.KpiTemplate;
import com.example.kpi.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for KPI Template entity - manages role-specific KPI templates with versioning
 */
@Repository
public interface KpiTemplateRepository extends JpaRepository<KpiTemplate, Long> {
    Optional<KpiTemplate> findByTemplateCode(String templateCode);

    List<KpiTemplate> findByTemplateCodeOrderByVersionNumberDesc(String templateCode);

    List<KpiTemplate> findByStatusAndActiveTrue(KpiTemplate.TemplateStatus status);

    List<KpiTemplate> findByRole(Role role);

    List<KpiTemplate> findByRoleAndStatusAndActiveTrue(Role role, KpiTemplate.TemplateStatus status);

    List<KpiTemplate> findByStatusAndActiveTrueOrderByTemplateNameAsc(KpiTemplate.TemplateStatus status);

    List<KpiTemplate> findByActiveTrue();

    List<KpiTemplate> findByStatusNot(KpiTemplate.TemplateStatus status);
}

