package com.example.kpi.repository;

import com.example.kpi.entity.KpiFrequency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface KpiFrequencyRepository extends JpaRepository<KpiFrequency, Long> {
    Optional<KpiFrequency> findByFrequencyCode(String frequencyCode);
    List<KpiFrequency> findByActiveOrderByDisplayOrder(Boolean active);
}

