package com.example.kpi.repository;

import com.example.kpi.entity.KpiMetricBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KpiMetricBindingRepository extends JpaRepository<KpiMetricBinding, Long> {

    Optional<KpiMetricBinding> findByKpiMasterIdAndActiveTrue(Long kpiMasterId);

    Optional<KpiMetricBinding> findByKpiMasterId(Long kpiMasterId);

    List<KpiMetricBinding> findByActiveTrue();
}

