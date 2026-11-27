package com.example.kpi.service;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import com.example.kpi.repository.KPIRepository;
import com.example.kpi.repository.ReviewCycleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiReminderServiceTest {

    @Mock
    private KPIRepository kpiRepository;
    @Mock
    private ReviewCycleRepository reviewCycleRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private KpiReminderService service;

    private User user(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setFirstName(name);
        u.setEmail(name.toLowerCase() + "@example.invalid");
        return u;
    }

    private Kpi kpi(User owner, User reviewer) {
        Kpi k = new Kpi();
        k.setUser(owner);
        k.setReviewer(reviewer);
        return k;
    }

    // ── Deadline reminders ────────────────────────────────────────────────────

    @Test
    void sendDeadlineReminders_shouldNotifyOncePerEmployee_notPerKpi() {
        ReviewCycle cycle = ReviewCycle.builder()
                .id(1L)
                .cycleCode("Q3_2026")
                .cycleName("Q3 Review")
                .endDate(LocalDate.now().plusDays(3))
                .status(ReviewCycle.CycleStatus.OPEN)
                .build();

        User alice = user(10L, "Alice");

        when(reviewCycleRepository.findByStatusIn(any())).thenReturn(List.of(cycle));
        when(kpiRepository.findByReviewCycleAndStatusIn(eq(cycle), any()))
                .thenReturn(List.of(kpi(alice, null), kpi(alice, null), kpi(alice, null)));

        int sent = service.sendDeadlineReminders();

        assertEquals(1, sent, "three KPIs for one employee must produce one reminder");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq(alice), anyString(), body.capture(),
                eq("KPI_DEADLINE_REMINDER"), eq("REVIEW_CYCLE"), eq(1L));
        assertTrue(body.getValue().contains("3 KPI(s)"));
    }

    @Test
    void sendDeadlineReminders_shouldSkipCycleEndingBeyondWarningWindow() {
        ReviewCycle cycle = ReviewCycle.builder()
                .id(2L)
                .cycleCode("Q4_2026")
                .endDate(LocalDate.now().plusDays(60))
                .status(ReviewCycle.CycleStatus.OPEN)
                .build();

        when(reviewCycleRepository.findByStatusIn(any())).thenReturn(List.of(cycle));

        assertEquals(0, service.sendDeadlineReminders());
        verify(notificationService, never()).create(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sendDeadlineReminders_shouldSkipCycleAlreadyPastEndDate() {
        ReviewCycle cycle = ReviewCycle.builder()
                .id(3L)
                .cycleCode("Q1_2026")
                .endDate(LocalDate.now().minusDays(1))
                .status(ReviewCycle.CycleStatus.OPEN)
                .build();

        when(reviewCycleRepository.findByStatusIn(any())).thenReturn(List.of(cycle));

        assertEquals(0, service.sendDeadlineReminders());
    }

    @Test
    void sendDeadlineReminders_shouldTolerateCycleWithoutEndDate() {
        ReviewCycle cycle = ReviewCycle.builder()
                .id(4L)
                .cycleCode("NO_END")
                .status(ReviewCycle.CycleStatus.OPEN)
                .build();

        when(reviewCycleRepository.findByStatusIn(any())).thenReturn(List.of(cycle));

        assertEquals(0, service.sendDeadlineReminders());
    }

    // ── Stale review reminders ────────────────────────────────────────────────

    @Test
    void sendStaleReviewReminders_shouldGroupByReviewer() {
        User alice = user(10L, "Alice");
        User bob = user(11L, "Bob");
        User manager = user(99L, "Manager");

        when(kpiRepository.findByStatusInAndSubmittedAtBefore(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(kpi(alice, manager), kpi(bob, manager)));

        int sent = service.sendStaleReviewReminders();

        assertEquals(1, sent, "both KPIs share a reviewer, so one reminder is enough");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(eq(manager), anyString(), body.capture(),
                eq("KPI_REVIEW_OVERDUE"), eq("KPI"), eq(null));
        assertTrue(body.getValue().contains("2 KPI submission(s)"));
    }

    @Test
    void sendStaleReviewReminders_shouldFallBackToReportingManager() {
        User manager = user(99L, "Manager");
        User alice = user(10L, "Alice");
        alice.setReportingManager(manager);

        when(kpiRepository.findByStatusInAndSubmittedAtBefore(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(kpi(alice, null)));

        assertEquals(1, service.sendStaleReviewReminders());
        verify(notificationService).create(eq(manager), anyString(), anyString(),
                eq("KPI_REVIEW_OVERDUE"), eq("KPI"), eq(null));
    }

    @Test
    void sendStaleReviewReminders_shouldSkipKpiWithNoResolvableReviewer() {
        User orphan = user(10L, "Orphan");

        when(kpiRepository.findByStatusInAndSubmittedAtBefore(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(kpi(orphan, null)));

        assertEquals(0, service.sendStaleReviewReminders());
        verify(notificationService, never()).create(any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    // ── Scheduled entry point ─────────────────────────────────────────────────

    @Test
    void sendScheduledReminders_shouldRunStaleSweep_evenWhenDeadlineSweepThrows() {
        when(reviewCycleRepository.findByStatusIn(any()))
                .thenThrow(new IllegalStateException("db down"));
        when(kpiRepository.findByStatusInAndSubmittedAtBefore(any(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // Must not propagate — a scheduled task that throws stops being useful.
        service.sendScheduledReminders();

        verify(kpiRepository).findByStatusInAndSubmittedAtBefore(any(), any(LocalDateTime.class));
    }

    @Test
    void sendDeadlineReminders_shouldOnlyQueryActiveCycles() {
        when(reviewCycleRepository.findByStatusIn(any())).thenReturn(List.of());

        service.sendDeadlineReminders();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ReviewCycle.CycleStatus>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(reviewCycleRepository).findByStatusIn(captor.capture());

        Collection<ReviewCycle.CycleStatus> statuses = captor.getValue();
        assertTrue(statuses.contains(ReviewCycle.CycleStatus.OPEN));
        assertTrue(statuses.contains(ReviewCycle.CycleStatus.IN_PROGRESS));
        assertTrue(!statuses.contains(ReviewCycle.CycleStatus.CLOSED));
    }
}

