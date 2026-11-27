package com.example.kpi.repository;

import com.example.kpi.entity.ReviewCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Review Cycle entity - manages review periods for KPI evaluation
 */
@Repository
public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, Long> {
    Optional<ReviewCycle> findByCycleCode(String cycleCode);

    List<ReviewCycle> findAll();

    List<ReviewCycle> findByKpisGeneratedFalse();

    /** Cycles in the given states — used by the deadline reminder sweep. */
    List<ReviewCycle> findByStatusIn(Collection<ReviewCycle.CycleStatus> statuses);
}

