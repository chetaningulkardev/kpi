package com.example.kpi.service;

import com.example.kpi.dto.BulkManagerReviewRequest;
import com.example.kpi.dto.BulkReviewResultDto;
import com.example.kpi.dto.EvidenceCreateRequest;
import com.example.kpi.dto.ManagerReviewRequest;
import com.example.kpi.dto.SelfAssessmentRequest;
import com.example.kpi.dto.TeamAssignmentFlowDto;
import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.KpiStatusHistory;
import com.example.kpi.entity.Role;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import com.example.kpi.repository.KPIRepository;
import com.example.kpi.repository.KpiEvidenceRepository;
import com.example.kpi.repository.KpiStatusHistoryRepository;
import com.example.kpi.repository.ReviewCycleRepository;
import com.example.kpi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiReviewWorkflowServiceTest {

    @Mock
    private KPIRepository kpiRepository;

    @Mock
    private KpiEvidenceRepository evidenceRepository;

    @Mock
    private ReviewCycleRepository reviewCycleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KpiScoringService scoringService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private KpiStatusHistoryRepository statusHistoryRepository;

    @InjectMocks
    private KpiReviewWorkflowService service;

    private User user;
    private User reviewer;
    private Kpi kpi;
    private ReviewCycle openCycle;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setEmail("user@example.invalid");
        user.setFirstName("Dev");
        user.setLastName("One");

        reviewer = new User();
        reviewer.setId(8L);
        reviewer.setEmail("manager@example.invalid");
        reviewer.setFirstName("Manager");
        reviewer.setLastName("One");

        Role adminRole = new Role();
        adminRole.setRoleName("ADMIN");
        adminRole.setRoleCode("ADMIN");
        reviewer.setRoles(Set.of(adminRole));

        user.setReportingManager(reviewer);

        openCycle = new ReviewCycle();
        openCycle.setCycleCode("Q3-2026");
        openCycle.setStatus(ReviewCycle.CycleStatus.OPEN);

        kpi = Kpi.builder().id(99L).user(user).status(Kpi.KpiStatus.PENDING).build();
        kpi.setKpiName("Delivery KPI");
        kpi.setReviewCycle(openCycle);
    }

    @Test
    void submitSelfAssessment_shouldUpdateAndSubmit_whenOwnerMatches() {
        SelfAssessmentRequest req = new SelfAssessmentRequest();
        req.setActualValue(88.2);
        req.setComments("Done");
        req.setJustification("Delivered milestone");

        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));
        when(kpiRepository.save(kpi)).thenReturn(kpi);

        Kpi result = service.submitSelfAssessment(99L, user, req);

        assertEquals(Kpi.KpiStatus.SUBMITTED, result.getStatus());
        assertEquals(88.2, result.getActualValue());
        assertEquals("Done", result.getComments());
        assertEquals("Delivered milestone", result.getJustification());
        verify(notificationService, times(1)).create(
                eq(reviewer),
                eq("KPI submitted for review"),
                contains("submitted KPI"),
                eq("KPI_SUBMITTED"),
                eq("KPI"),
                eq(99L)
        );
    }

    @Test
    void submitSelfAssessment_shouldFail_whenKpiDoesNotBelongToCurrentUser() {
        User another = new User();
        another.setId(10L);
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        assertThrows(SecurityException.class, () -> service.submitSelfAssessment(99L, another, new SelfAssessmentRequest()));
    }

    @Test
    void submitManagerReview_shouldDelegateToScoringAndReturnKpi() {
        kpi.setStatus(Kpi.KpiStatus.SUBMITTED);
        ManagerReviewRequest req = new ManagerReviewRequest();
        req.setRating(4.5);
        req.setReviewerComments("Good work");

        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi), Optional.of(kpi));

        Kpi result = service.submitManagerReview(99L, reviewer, req);

        verify(scoringService).applyDecision(99L, 4.5, "Good work", reviewer, Kpi.KpiStatus.APPROVED);
        verify(notificationService, times(1)).create(
                eq(user),
                eq("KPI approved"),
                contains("approved KPI"),
                eq("KPI_REVIEWED"),
                eq("KPI"),
                eq(99L)
        );
        assertNotNull(result);
        assertEquals(99L, result.getId());
    }

    @Test
    void getTeamAssignmentFlow_shouldGroupByTransitionAndCountUsers() {
        Role developer = new Role();
        developer.setRoleName("Developer");
        developer.setRoleCode("DEVELOPER");

        Role techFocal = new Role();
        techFocal.setRoleName("Tech Focal");
        techFocal.setRoleCode("TECH_FOCAL");
        techFocal.setParentRole(developer);

        user.setRoles(Set.of(developer));
        reviewer.setRoles(Set.of(techFocal));

        User user2 = new User();
        user2.setId(9L);
        user2.setFirstName("Dev");
        user2.setLastName("Two");
        user2.setEmail("dev2@example.invalid");
        user2.setRoles(Set.of(developer));
        user2.setReportingManager(reviewer);

        ReviewCycle cycle = new ReviewCycle();
        cycle.setCycleCode("Q3-2026");

        Kpi k1 = Kpi.builder().id(1L).user(user).status(Kpi.KpiStatus.SUBMITTED).kpiName("K1").build();
        k1.setReviewer(reviewer);
        k1.setReviewCycle(cycle);

        Kpi k2 = Kpi.builder().id(2L).user(user).status(Kpi.KpiStatus.SUBMITTED).kpiName("K2").build();
        k2.setReviewer(reviewer);
        k2.setReviewCycle(cycle);

        Kpi k3 = Kpi.builder().id(3L).user(user2).status(Kpi.KpiStatus.SUBMITTED).kpiName("K3").build();
        k3.setReviewer(reviewer);
        k3.setReviewCycle(cycle);

        // reviewer carries TECH_FOCAL here (not ADMIN), so the scoped query is used
        when(kpiRepository.findPendingForReviewer(eq(8L), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(k1, k2, k3)));

        List<TeamAssignmentFlowDto> flows = service.getTeamAssignmentFlow(reviewer);

        assertEquals(1, flows.size());
        TeamAssignmentFlowDto flow = flows.get(0);
        assertEquals(2, flow.getPendingUsers());
        assertEquals(3, flow.getPendingKpis());
        assertTrue(flow.getTransitionKey().contains("DEVELOPER"));
        assertTrue(flow.getTransitionKey().contains("TECH_FOCAL"));
    }

    @Test
    void getMyKpis_shouldFail_whenCycleCodeMissing() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getMyKpis("", 7L));
        assertTrue(ex.getMessage().contains("cycleCode"));
    }

    // ── Workflow state guards ───────────────────────────────────────────────

    @Test
    void submitSelfAssessment_shouldReject_whenKpiAlreadyApproved() {
        kpi.setStatus(Kpi.KpiStatus.APPROVED);
        SelfAssessmentRequest req = new SelfAssessmentRequest();
        req.setActualValue(10.0);
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.submitSelfAssessment(99L, user, req));
        assertTrue(ex.getMessage().contains("APPROVED"));
        verify(kpiRepository, never()).save(any());
    }

    @Test
    void submitSelfAssessment_shouldAllowResubmit_afterReworkRequested_andClearStaleRating() {
        kpi.setStatus(Kpi.KpiStatus.REWORK_REQUIRED);
        kpi.setRating(2.0);
        kpi.setReviewerComments("Needs more detail");

        SelfAssessmentRequest req = new SelfAssessmentRequest();
        req.setActualValue(42.0);
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));
        when(kpiRepository.save(kpi)).thenReturn(kpi);

        Kpi result = service.submitSelfAssessment(99L, user, req);

        assertEquals(Kpi.KpiStatus.SUBMITTED, result.getStatus());
        assertNull(result.getRating(), "previous reviewer verdict must not carry forward");
        assertNull(result.getReviewerComments());
        assertNotNull(result.getSubmittedAt());
    }

    @Test
    void submitSelfAssessment_shouldReject_whenActualValueMissing() {
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitSelfAssessment(99L, user, new SelfAssessmentRequest()));
        assertTrue(ex.getMessage().contains("actualValue"));
    }

    @Test
    void submitSelfAssessment_shouldReject_whenCycleIsClosed() {
        openCycle.setStatus(ReviewCycle.CycleStatus.CLOSED);
        SelfAssessmentRequest req = new SelfAssessmentRequest();
        req.setActualValue(10.0);
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.submitSelfAssessment(99L, user, req));
        assertTrue(ex.getMessage().contains("CLOSED"));
    }

    @Test
    void submitSelfAssessment_shouldWriteAuditTrail() {
        SelfAssessmentRequest req = new SelfAssessmentRequest();
        req.setActualValue(88.2);
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));
        when(kpiRepository.save(kpi)).thenReturn(kpi);

        service.submitSelfAssessment(99L, user, req);

        verify(statusHistoryRepository, times(1)).save(any(KpiStatusHistory.class));
    }

    // ── Reject / rework ─────────────────────────────────────────────────────

    @Test
    void submitManagerReview_shouldSupportReworkDecision() {
        kpi.setStatus(Kpi.KpiStatus.SUBMITTED);
        ManagerReviewRequest req = new ManagerReviewRequest();
        req.setDecision("REWORK");
        req.setReviewerComments("Please attach evidence");
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        service.submitManagerReview(99L, reviewer, req);

        verify(scoringService).applyDecision(99L, null, "Please attach evidence", reviewer,
                Kpi.KpiStatus.REWORK_REQUIRED);
        verify(notificationService).create(eq(user), eq("KPI sent back for rework"), anyString(),
                eq("KPI_REWORK_REQUIRED"), eq("KPI"), eq(99L));
    }

    @Test
    void submitManagerReview_shouldSupportRejectDecision() {
        kpi.setStatus(Kpi.KpiStatus.SUBMITTED);
        ManagerReviewRequest req = new ManagerReviewRequest();
        req.setDecision("REJECT");
        req.setReviewerComments("Out of scope for this cycle");
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        service.submitManagerReview(99L, reviewer, req);

        verify(scoringService).applyDecision(99L, null, "Out of scope for this cycle", reviewer,
                Kpi.KpiStatus.REJECTED);
    }

    @Test
    void submitManagerReview_shouldRequireComments_whenSendingBack() {
        kpi.setStatus(Kpi.KpiStatus.SUBMITTED);
        ManagerReviewRequest req = new ManagerReviewRequest();
        req.setDecision("REWORK");
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitManagerReview(99L, reviewer, req));
        assertTrue(ex.getMessage().contains("reviewerComments"));
    }

    @Test
    void submitManagerReview_shouldReject_whenKpiNotSubmitted() {
        kpi.setStatus(Kpi.KpiStatus.PENDING);
        ManagerReviewRequest req = new ManagerReviewRequest();
        req.setRating(4.0);
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        assertThrows(IllegalStateException.class, () -> service.submitManagerReview(99L, reviewer, req));
    }

    @Test
    void submitManagerReview_shouldReject_whenDecisionIsUnknown() {
        kpi.setStatus(Kpi.KpiStatus.SUBMITTED);
        ManagerReviewRequest req = new ManagerReviewRequest();
        req.setDecision("MAYBE");
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        assertThrows(IllegalArgumentException.class, () -> service.submitManagerReview(99L, reviewer, req));
    }

    // ── Bulk review ─────────────────────────────────────────────────────────

    @Test
    void submitBulkManagerReview_shouldReportPerKpiFailuresWithoutAbortingBatch() {
        Kpi ok = Kpi.builder().id(1L).user(user).status(Kpi.KpiStatus.SUBMITTED).kpiName("OK").build();
        ok.setReviewCycle(openCycle);
        Kpi bad = Kpi.builder().id(2L).user(user).status(Kpi.KpiStatus.APPROVED).kpiName("Bad").build();
        bad.setReviewCycle(openCycle);

        when(kpiRepository.findById(1L)).thenReturn(Optional.of(ok));
        when(kpiRepository.findById(2L)).thenReturn(Optional.of(bad));

        BulkManagerReviewRequest request = new BulkManagerReviewRequest();
        request.setKpiIds(List.of(1L, 2L));
        ManagerReviewRequest review = new ManagerReviewRequest();
        review.setRating(4.0);
        request.setReview(review);

        BulkReviewResultDto result = service.submitBulkManagerReview(request, reviewer);

        assertEquals(2, result.requested());
        assertEquals(1, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals(2L, result.failures().get(0).kpiId());
    }

    // ── Evidence authorisation (IDOR protection) ────────────────────────────

    @Test
    void getEvidence_shouldReject_whenRequesterIsUnrelatedToKpi() {
        User stranger = new User();
        stranger.setId(404L);
        stranger.setRoles(Set.of());
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        assertThrows(SecurityException.class, () -> service.getEvidence(99L, stranger));
    }

    @Test
    void getEvidence_shouldAllow_forKpiOwner() {
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));
        when(evidenceRepository.findByKpi(kpi)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getEvidence(99L, user));
    }

    @Test
    void getEvidence_shouldAllow_forReportingManager() {
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));
        when(evidenceRepository.findByKpi(kpi)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.getEvidence(99L, reviewer));
    }

    @Test
    void addEvidence_shouldReject_whenRequesterIsUnrelatedToKpi() {
        User stranger = new User();
        stranger.setId(404L);
        stranger.setRoles(Set.of());
        when(kpiRepository.findById(99L)).thenReturn(Optional.of(kpi));

        assertThrows(SecurityException.class,
                () -> service.addEvidence(99L, stranger, new EvidenceCreateRequest()));
        verify(evidenceRepository, never()).save(any());
    }
}
