package com.example.kpi.repository;

import com.example.kpi.entity.ScoringConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Scoring Configuration entity - manages rating scales and scoring rules
 */
@Repository
public interface ScoringConfigurationRepository extends JpaRepository<ScoringConfiguration, Long> {
    Optional<ScoringConfiguration> findByScaleCode(String scaleCode);

    List<ScoringConfiguration> findByActiveTrue();
}

