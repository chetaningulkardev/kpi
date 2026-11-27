package com.example.kpi.repository;

import com.example.kpi.entity.KpiTemplate;
import com.example.kpi.entity.KpiTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for KPI Template Item entity - manages KPI to template mappings
 */
@Repository
public interface KpiTemplateItemRepository extends JpaRepository<KpiTemplateItem, Long> {
    List<KpiTemplateItem> findByTemplate(KpiTemplate template);

    List<KpiTemplateItem> findByTemplateOrderBySequenceOrder(KpiTemplate template);

    List<KpiTemplateItem> findByTemplateAndActiveTrue(KpiTemplate template);
}

