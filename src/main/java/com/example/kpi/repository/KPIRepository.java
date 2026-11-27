package com.example.kpi.repository;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for KPI entity - manages individual KPI instances for employees in review cycles
 */
@Repository
public interface KPIRepository extends JpaRepository<Kpi, Long> {

    /**
     * {@code Kpi} maps user, reviewCycle and kpiMaster as eager
     * {@code @ManyToOne}. Eager does not mean joined: for a query returning a
     * list, Hibernate loads the rows and then issues a further select per
     * association per row. A page of 50 KPIs becomes upwards of 150 extra round
     * trips, and the review queue is the busiest screen in the product.
     *
     * <p>The {@code @EntityGraph} on the list queries below makes it one join
     * instead. Safe to combine with {@code Pageable} because these associations
     * are all single-valued — a collection would force Hibernate to sort and
     * page in memory after loading everything.
     */
    List<Kpi> findByReviewCycleAndUser(ReviewCycle reviewCycle, User user);

    List<Kpi> findByReviewCycle(ReviewCycle reviewCycle);

    @EntityGraph(attributePaths = {"user", "reviewCycle", "kpiMaster"})
    List<Kpi> findByUser(User user);

    List<Kpi> findByStatus(Kpi.KpiStatus status);

    @EntityGraph(attributePaths = {"user", "reviewCycle", "kpiMaster"})
    List<Kpi> findByUserOrderByIdDesc(User user);

    Optional<Kpi> findByReviewCycleAndUserAndId(ReviewCycle reviewCycle, User user, Long id);

    long countByReviewCycleAndUser(ReviewCycle reviewCycle, User user);

    /**
     * Pending reviews scoped to a single reviewer, resolved in SQL rather than by
     * loading every submitted KPI in the org and filtering in memory.
     *
     * <p>A KPI is in a reviewer's queue when it is explicitly assigned to them
     * ({@code reviewer_id}) or when they are the employee's reporting manager.</p>
     */
    @EntityGraph(attributePaths = {"user", "reviewCycle", "kpiMaster", "reviewer"})
    @Query("""
            SELECT k FROM Kpi k
            WHERE k.status IN :statuses
              AND (k.reviewer.id = :reviewerId OR k.user.reportingManager.id = :reviewerId)
            """)
    Page<Kpi> findPendingForReviewer(@Param("reviewerId") Long reviewerId,
                                     @Param("statuses") Collection<Kpi.KpiStatus> statuses,
                                     Pageable pageable);

    /**
     * Reviewer queue widened by org-structure scope: explicit reviewer, reporting
     * line, or any employee inside the reviewer's accessible squads. The owner is
     * excluded so nobody ever reviews their own KPI.
     */
    @EntityGraph(attributePaths = {"user", "reviewCycle", "kpiMaster", "reviewer"})
    @Query("""
            SELECT k FROM Kpi k
            WHERE k.status IN :statuses
              AND k.user.id <> :reviewerId
              AND (k.reviewer.id = :reviewerId
                   OR k.user.reportingManager.id = :reviewerId
                   OR k.user.id IN :accessibleUserIds)
            """)
    Page<Kpi> findPendingForReviewerInScope(@Param("reviewerId") Long reviewerId,
                                            @Param("accessibleUserIds") Collection<Long> accessibleUserIds,
                                            @Param("statuses") Collection<Kpi.KpiStatus> statuses,
                                            Pageable pageable);

    /** Admin variant: every KPI awaiting review, regardless of reviewer. */
    @EntityGraph(attributePaths = {"user", "reviewCycle", "kpiMaster", "reviewer"})
    Page<Kpi> findByStatusIn(Collection<Kpi.KpiStatus> statuses, Pageable pageable);

    List<Kpi> findByReviewCycleAndStatusIn(ReviewCycle reviewCycle, Collection<Kpi.KpiStatus> statuses);

    /** KPIs that have been waiting on a reviewer since before the given instant. */
    List<Kpi> findByStatusInAndSubmittedAtBefore(Collection<Kpi.KpiStatus> statuses, LocalDateTime submittedBefore);
}
