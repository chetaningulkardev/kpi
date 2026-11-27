package com.example.kpi.repository;

import com.example.kpi.entity.KpiScorecard;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for KPI Scorecard entity - manages auto-generated scorecards for employees
 */
@Repository
public interface KpiScorecardRepository extends JpaRepository<KpiScorecard, Long> {
    List<KpiScorecard> findByReviewCycle(ReviewCycle reviewCycle);

    Optional<KpiScorecard> findByReviewCycleAndUser(ReviewCycle reviewCycle, User user);

    List<KpiScorecard> findByUser(User user);

    /**
     * Oldest cycle first. A trend read from {@link #findByUser} would be in
     * whatever order the rows happened to come back in, which is insertion
     * order often enough to look correct in testing and wrong in production.
     *
     * <p>The cycle is join-fetched: every row's start date and name are read
     * straight afterwards to build the chart, so leaving it to the eager
     * per-row select would issue one query per cycle in someone's history.
     */
    @EntityGraph(attributePaths = {"reviewCycle", "user"})
    List<KpiScorecard> findByUserOrderByReviewCycleStartDateAsc(User user);

    /** Employee-facing history: only what the organisation has released. */
    @EntityGraph(attributePaths = {"reviewCycle", "user"})
    List<KpiScorecard> findByUserAndStatusOrderByReviewCycleStartDateAsc(
            User user, KpiScorecard.ScorecardStatus status);

    List<KpiScorecard> findByStatus(KpiScorecard.ScorecardStatus status);

    @EntityGraph(attributePaths = {"reviewCycle", "user"})
    List<KpiScorecard> findByReviewCycleAndStatus(ReviewCycle reviewCycle, KpiScorecard.ScorecardStatus status);

    long countByReviewCycle(ReviewCycle reviewCycle);
}

