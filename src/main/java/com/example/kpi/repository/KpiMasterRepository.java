package com.example.kpi.repository;

import com.example.kpi.entity.KpiCategory;
import com.example.kpi.entity.KpiFrequency;
import com.example.kpi.entity.KpiMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for KPI Master entity - manages reusable KPI definitions
 */
@Repository
public interface KpiMasterRepository extends JpaRepository<KpiMaster, Long> {
    Optional<KpiMaster> findByKpiCode(String kpiCode);

    List<KpiMaster> findByCategory(KpiCategory category);

    List<KpiMaster> findDistinctByRoles_Id(Long roleId);

    List<KpiMaster> findDistinctByCategoryAndRoles_Id(KpiCategory category, Long roleId);

    @Query("SELECT COUNT(km) FROM KpiMaster km JOIN km.roles r WHERE km.id = :kpiMasterId AND r.id = :roleId")
    long countRoleMapping(@Param("kpiMasterId") Long kpiMasterId, @Param("roleId") Long roleId);

    List<KpiMaster> findByActiveTrue();

    List<KpiMaster> findByActiveTrueAndCategory(KpiCategory category);

    long countByFrequency(KpiFrequency frequency);

    long countByDataSource(String dataSource);
}

