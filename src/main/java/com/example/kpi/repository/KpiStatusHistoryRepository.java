package com.example.kpi.repository;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.KpiStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the KPI status-transition audit trail.
 */
@Repository
public interface KpiStatusHistoryRepository extends JpaRepository<KpiStatusHistory, Long> {

    List<KpiStatusHistory> findByKpiOrderByChangedAtAsc(Kpi kpi);

    List<KpiStatusHistory> findByKpiIdOrderByChangedAtAsc(Long kpiId);
}

