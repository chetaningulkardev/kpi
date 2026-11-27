package com.example.kpi.repository;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.KpiEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for KPI Evidence entity - manages evidence for KPI submissions
 */
@Repository
public interface KpiEvidenceRepository extends JpaRepository<KpiEvidence, Long> {
    List<KpiEvidence> findByKpi(Kpi kpi);

    /** Used to authorise downloads by mapping a stored file back to its KPI. */
    Optional<KpiEvidence> findByContentUrl(String contentUrl);
}

