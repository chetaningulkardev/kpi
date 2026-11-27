package com.example.kpi.service;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import com.example.kpi.repository.KPIRepository;
import com.example.kpi.repository.ReviewCycleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nightly reminder sweep.
 *
 * <p>Without this, a KPI can sit unsubmitted until the cycle closes, or wait
 * weeks on an unaware reviewer, and nothing ever chases it.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KpiReminderService {

    private final KPIRepository kpiRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final NotificationService notificationService;

    /** Statuses that still need action from the employee. */
    private static final Set<Kpi.KpiStatus> EMPLOYEE_ACTION_STATUSES =
            Set.of(Kpi.KpiStatus.PENDING, Kpi.KpiStatus.REWORK_REQUIRED, Kpi.KpiStatus.REJECTED);

    /** Statuses sitting in a reviewer's queue. */
    private static final Set<Kpi.KpiStatus> REVIEWER_ACTION_STATUSES =
            Set.of(Kpi.KpiStatus.SUBMITTED, Kpi.KpiStatus.UNDER_REVIEW);

    private static final Set<ReviewCycle.CycleStatus> ACTIVE_CYCLES = Set.of(
            ReviewCycle.CycleStatus.OPEN,
            ReviewCycle.CycleStatus.IN_PROGRESS,
            ReviewCycle.CycleStatus.UNDER_REVIEW
    );

    /** Cycle is "closing soon" within this many days of its end date. */
    private static final long DEADLINE_WARNING_DAYS = 7;

    /** A submission waiting longer than this is escalated to the reviewer. */
    private static final long STALE_REVIEW_DAYS = 5;

    /**
     * Runs every morning at 08:00. Both sweeps are independent, so a failure in
     * one must not prevent the other from running.
     */
    @Scheduled(cron = "${app.reminders.cron:0 0 8 * * *}")
    public void sendScheduledReminders() {
        try {
            int deadline = sendDeadlineReminders();
            log.info("Deadline reminder sweep sent {} notification(s)", deadline);
        } catch (Exception ex) {
            log.error("Deadline reminder sweep failed", ex);
        }
        try {
            int stale = sendStaleReviewReminders();
            log.info("Stale review sweep sent {} notification(s)", stale);
        } catch (Exception ex) {
            log.error("Stale review sweep failed", ex);
        }
    }

    /**
     * Warn employees who still have unsubmitted KPIs in a cycle that is about to
     * close. One notification per employee per cycle, not one per KPI.
     *
     * @return number of notifications created
     */
    @Transactional
    public int sendDeadlineReminders() {
        LocalDate today = LocalDate.now();
        int sent = 0;

        for (ReviewCycle cycle : reviewCycleRepository.findByStatusIn(ACTIVE_CYCLES)) {
            if (cycle == null || cycle.getEndDate() == null) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, cycle.getEndDate());
            if (daysLeft < 0 || daysLeft > DEADLINE_WARNING_DAYS) {
                continue;
            }

            List<Kpi> outstanding = kpiRepository.findByReviewCycleAndStatusIn(cycle, EMPLOYEE_ACTION_STATUSES);

            // Group per employee so nobody gets ten emails for ten KPIs.
            Map<Long, PendingBucket> byUser = new LinkedHashMap<>();
            for (Kpi kpi : outstanding) {
                User owner = kpi.getUser();
                if (owner == null || owner.getId() == null) {
                    continue;
                }
                byUser.computeIfAbsent(owner.getId(), id -> new PendingBucket(owner)).count++;
            }

            for (PendingBucket bucket : byUser.values()) {
                notificationService.create(
                        bucket.user,
                        "KPI deadline approaching",
                        String.format("You have %d KPI(s) awaiting submission for %s. The cycle closes on %s (%s).",
                                bucket.count,
                                cycle.getCycleName() != null ? cycle.getCycleName() : cycle.getCycleCode(),
                                cycle.getEndDate(),
                                daysLeft == 0 ? "today" : "in " + daysLeft + " day(s)"),
                        "KPI_DEADLINE_REMINDER",
                        "REVIEW_CYCLE",
                        cycle.getId()
                );
                sent++;
            }
        }
        return sent;
    }

    /**
     * Nudge reviewers about submissions that have been waiting too long.
     *
     * @return number of notifications created
     */
    @Transactional
    public int sendStaleReviewReminders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(STALE_REVIEW_DAYS);
        List<Kpi> stale = kpiRepository.findByStatusInAndSubmittedAtBefore(REVIEWER_ACTION_STATUSES, cutoff);

        Map<Long, PendingBucket> byReviewer = new LinkedHashMap<>();
        for (Kpi kpi : stale) {
            User reviewer = resolveReviewer(kpi);
            if (reviewer == null || reviewer.getId() == null) {
                continue;
            }
            byReviewer.computeIfAbsent(reviewer.getId(), id -> new PendingBucket(reviewer)).count++;
        }

        for (PendingBucket bucket : byReviewer.values()) {
            notificationService.create(
                    bucket.user,
                    "KPI reviews are overdue",
                    String.format("%d KPI submission(s) have been waiting more than %d days for your review.",
                            bucket.count, STALE_REVIEW_DAYS),
                    "KPI_REVIEW_OVERDUE",
                    "KPI",
                    null
            );
        }
        return byReviewer.size();
    }

    /** Explicit reviewer wins; otherwise fall back to the owner's line manager. */
    private User resolveReviewer(Kpi kpi) {
        if (kpi == null) {
            return null;
        }
        if (kpi.getReviewer() != null) {
            return kpi.getReviewer();
        }
        return kpi.getUser() != null ? kpi.getUser().getReportingManager() : null;
    }

    /** Mutable per-recipient tally used while grouping. */
    private static final class PendingBucket {
        private final User user;
        private int count;

        private PendingBucket(User user) {
            this.user = user;
        }
    }
}


