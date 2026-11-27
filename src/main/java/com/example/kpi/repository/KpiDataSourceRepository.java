package com.example.kpi.repository;

import com.example.kpi.entity.KpiDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KpiDataSourceRepository extends JpaRepository<KpiDataSource, Long> {
    Optional<KpiDataSource> findBySourceCode(String sourceCode);
    List<KpiDataSource> findByActiveOrderByDisplayOrder(Boolean active);
}

