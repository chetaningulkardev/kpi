package com.example.kpi.service;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.KpiScorecard;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import com.example.kpi.repository.KPIRepository;
import com.example.kpi.repository.KpiScorecardRepository;
import com.example.kpi.repository.ReviewCycleRepository;
import com.example.kpi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiScoringServiceTest {

    @Mock
    private KPIRepository kpiRepository;

    @Mock
    private KpiScorecardRepository scorecardRepository;

    @Mock
    private ReviewCycleRepository reviewCycleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KpiScoringService service;

    private User employee;
    private User reviewer;
    private ReviewCycle cycle;

    @BeforeEach
    void setUp() {
        employee = new User();
        employee.setId(1L);
        employee.setFirstName("Dev");
        employee.setLastName("One");

        reviewer = new User();
        reviewer.setId(2L);
        reviewer.setFirstName("Manager");
        reviewer.setLastName("One");

        cycle = new ReviewCycle();
        cycle.setId(5L);
        cycle.setCycleCode("Q3-2026");
        cycle.setCycleName("Q3 Review");

        when(reviewCycleRepository.findByCycleCode("Q3-2026")).thenReturn(Optional.of(cycle));
        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(scorecardRepository.save(any(KpiScorecard.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Kpi kpi(long id, double weight, Double rating, String category) {
        Kpi k = Kpi.builder()
                .id(id)
                .user(employee)
                .reviewCycle(cycle)
                .weight(weight)
                .rating(rating)
                .categoryName(category)
                .status(Kpi.KpiStatus.APPROVED)
                .build();
        k.setKpiName("KPI " + id);
        return k;
    }

    // ── Decisions ───────────────────────────────────────────────────────────

    @Test
    void applyDecision_shouldApproveAndKeepRating() {
        Kpi k = kpi(10L, 50, null, "Delivery");
        when(kpiRepository.findById(10L)).thenReturn(Optional.of(k));

        service.applyDecision(10L, 4.0, "Nice", reviewer, Kpi.KpiStatus.APPROVED);

        assertEquals(Kpi.KpiStatus.APPROVED, k.getStatus());
        assertEquals(4.0, k.getRating());
        assertEquals(reviewer, k.getReviewer());
        assertNotNull(k.getReviewedAt());
    }

    @Test
    void applyDecision_shouldRequireRatingWhenApproving() {
        assertThrows(IllegalArgumentException.class,
                () -> service.applyDecision(10L, null, "x", reviewer, Kpi.KpiStatus.APPROVED));
    }

    @Test
    void applyDecision_shouldClearStaleRatingWhenSendingBack() {
        Kpi k = kpi(11L, 50, 3.0, "Delivery");
        when(kpiRepository.findById(11L)).thenReturn(Optional.of(k));

        service.applyDecision(11L, null, "Add evidence", reviewer, Kpi.KpiStatus.REWORK_REQUIRED);

        assertEquals(Kpi.KpiStatus.REWORK_REQUIRED, k.getStatus());
        assertNull(k.getRating(), "a returned KPI must not keep a score");
    }

    @Test
    void applyDecision_shouldRejectOutOfRangeRating() {
        Kpi k = kpi(12L, 50, null, "Delivery");
        when(kpiRepository.findById(12L)).thenReturn(Optional.of(k));

        assertThrows(IllegalArgumentException.class,
                () -> service.applyDecision(12L, 9.0, "x", reviewer, Kpi.KpiStatus.APPROVED));
    }

    @Test
    void rateKpi_shouldStillApprove_forBackwardCompatibility() {
        Kpi k = kpi(13L, 100, null, "Delivery");
        when(kpiRepository.findById(13L)).thenReturn(Optional.of(k));

        service.rateKpi(13L, 5.0, "Great", reviewer);

        assertEquals(Kpi.KpiStatus.APPROVED, k.getStatus());
        assertEquals(5.0, k.getRating());
    }

    // ── Scorecard generation ────────────────────────────────────────────────

    @Test
    void generateScorecard_shouldSumWeightedScores_notAverageThem() {
        // Two KPIs at 50% weight rated 4 and 5 => weighted average rating 4.5
        when(kpiRepository.findByReviewCycleAndUser(cycle, employee))
                .thenReturn(List.of(kpi(1L, 50, 4.0, "Delivery"), kpi(2L, 50, 5.0, "Quality")));
        when(scorecardRepository.findByReviewCycleAndUser(cycle, employee)).thenReturn(Optional.empty());

        KpiScorecard card = service.generateScorecard(employee, cycle, null);

        assertNotNull(card);
        assertEquals(4.5, card.getOverallScore(), 0.0001);
    }

    @Test
    void generateScorecard_shouldNotRequireATemplate() {
        when(kpiRepository.findByReviewCycleAndUser(cycle, employee))
                .thenReturn(List.of(kpi(1L, 100, 3.0, "Delivery")));
        when(scorecardRepository.findByReviewCycleAndUser(cycle, employee)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.generateScorecard(employee, cycle, null));
    }

    @Test
    void generateScorecard_shouldRefreshExistingCard_ratherThanDuplicating() {
        KpiScorecard existing = KpiScorecard.builder()
                .id(77L).reviewCycle(cycle).user(employee).overallScore(1.0).build();
        when(kpiRepository.findByReviewCycleAndUser(cycle, employee))
                .thenReturn(List.of(kpi(1L, 100, 4.0, "Delivery")));
        when(scorecardRepository.findByReviewCycleAndUser(cycle, employee)).thenReturn(Optional.of(existing));

        KpiScorecard card = service.generateScorecard(employee, cycle, null);

        assertEquals(77L, card.getId(), "should update in place, not create a second scorecard");
        assertEquals(4.0, card.getOverallScore(), 0.0001);
    }

    @Test
    void generateScorecard_shouldAggregateCategoryScores() {
        when(kpiRepository.findByReviewCycleAndUser(cycle, employee))
                .thenReturn(List.of(
                        kpi(1L, 25, 4.0, "Delivery"),
                        kpi(2L, 25, 2.0, "Delivery"),
                        kpi(3L, 50, 5.0, "Quality")));
        when(scorecardRepository.findByReviewCycleAndUser(cycle, employee)).thenReturn(Optional.empty());

        KpiScorecard card = service.generateScorecard(employee, cycle, null);

        // Delivery = 4*25/100 + 2*25/100 = 1.5 ; Quality = 5*50/100 = 2.5
        assertEquals(1.5, card.getCategoryScores().get("Delivery"), 0.0001);
        assertEquals(2.5, card.getCategoryScores().get("Quality"), 0.0001);
    }

    @Test
    void generateScorecardByCycleCode_shouldNotThrow_whenTemplateIsAbsent() {
        when(kpiRepository.findByReviewCycleAndUser(cycle, employee))
                .thenReturn(List.of(kpi(1L, 100, 4.0, "Delivery")));
        when(scorecardRepository.findByReviewCycleAndUser(cycle, employee)).thenReturn(Optional.empty());

        // Previously this always threw "Template cannot be null".
        assertDoesNotThrow(() -> service.generateScorecard("Q3-2026", 1L));
    }

    // ── Calibration ─────────────────────────────────────────────────────────

    @Test
    void calibrateScorecard_shouldApplyModeratedScoreAndAudit() {
        KpiScorecard card = KpiScorecard.builder()
                .id(3L).reviewCycle(cycle).user(employee)
                .overallScore(4.8).status(KpiScorecard.ScorecardStatus.DRAFT).build();
        when(scorecardRepository.findById(3L)).thenReturn(Optional.of(card));

        KpiScorecard result = service.calibrateScorecard(3L, 4.0, "Normalised across squad", "FINAL", reviewer);

        assertEquals(4.0, result.getOverallScore());
        assertEquals(KpiScorecard.ScorecardStatus.FINAL, result.getStatus());
        assertEquals(reviewer, result.getCalibratedBy());
        assertEquals("Normalised across squad", result.getCalibrationNotes());
    }

    @Test
    void calibrateScorecard_shouldRequireNotes() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calibrateScorecard(3L, 4.0, "  ", "FINAL", reviewer));
    }

    @Test
    void calibrateScorecard_shouldRejectPublishedScorecards() {
        KpiScorecard card = KpiScorecard.builder()
                .id(4L).reviewCycle(cycle).user(employee)
                .status(KpiScorecard.ScorecardStatus.PUBLISHED).build();
        when(scorecardRepository.findById(4L)).thenReturn(Optional.of(card));

        assertThrows(IllegalStateException.class,
                () -> service.calibrateScorecard(4L, 3.0, "late change", "FINAL", reviewer));
    }

    @Test
    void calibrateScorecard_shouldRejectOutOfRangeScore() {
        KpiScorecard card = KpiScorecard.builder()
                .id(5L).reviewCycle(cycle).user(employee)
                .status(KpiScorecard.ScorecardStatus.DRAFT).build();
        when(scorecardRepository.findById(5L)).thenReturn(Optional.of(card));

        assertThrows(IllegalArgumentException.class,
                () -> service.calibrateScorecard(5L, 7.0, "too high", "FINAL", reviewer));
    }

    @Test
    void calibrateScorecard_shouldRejectUnknownStatus() {
        KpiScorecard card = KpiScorecard.builder()
                .id(6L).reviewCycle(cycle).user(employee)
                .status(KpiScorecard.ScorecardStatus.DRAFT).build();
        when(scorecardRepository.findById(6L)).thenReturn(Optional.of(card));

        assertThrows(IllegalArgumentException.class,
                () -> service.calibrateScorecard(6L, 3.0, "notes", "BOGUS", reviewer));
    }

    @Test
    void calculateWeightedScore_shouldReturnNullWithoutRating() {
        assertNull(service.calculateWeightedScore(kpi(1L, 50, null, "Delivery")));
    }

    // ── What the employee is allowed to see ─────────────────────────────────

    @Test
    void getPublishedScorecardsForUser_shouldAskOnlyForPublishedCards() {
        service.getPublishedScorecardsForUser(1L);

        // The filter belongs in the query, not in a post-hoc stream: an
        // in-progress rating should never be loaded into a response object
        // that some future caller might forget to sift.
        verify(scorecardRepository).findByUserAndStatusOrderByReviewCycleStartDateAsc(
                employee, KpiScorecard.ScorecardStatus.PUBLISHED);
    }

    @Test
    void getPublishedScorecard_shouldReturnCardOncePublished() {
        KpiScorecard card = KpiScorecard.builder()
                .id(9L).reviewCycle(cycle).user(employee)
                .status(KpiScorecard.ScorecardStatus.PUBLISHED).build();
        when(scorecardRepository.findByReviewCycleAndUser(cycle, employee))
                .thenReturn(Optional.of(card));

        assertSame(card, service.getPublishedScorecard("Q3-2026", 1L));
    }

    @Test
    void getPublishedScorecard_shouldHideAnythingStillBeingCalibrated() {
        // FINAL is not a release gate — calibrateScorecard still accepts a card
        // in that state and can change the number.
        for (KpiScorecard.ScorecardStatus status : List.of(
                KpiScorecard.ScorecardStatus.DRAFT,
                KpiScorecard.ScorecardStatus.UNDER_REVIEW,
                KpiScorecard.ScorecardStatus.CALIBRATION,
                KpiScorecard.ScorecardStatus.FINAL)) {

            KpiScorecard card = KpiScorecard.builder()
                    .id(9L).reviewCycle(cycle).user(employee).status(status).build();
            when(scorecardRepository.findByReviewCycleAndUser(cycle, employee))
                    .thenReturn(Optional.of(card));

            assertNull(service.getPublishedScorecard("Q3-2026", 1L),
                    status + " must not be visible to the employee");
        }
    }

    @Test
    void getPublishedScorecard_shouldReturnNullWhenNoCardExists() {
        when(scorecardRepository.findByReviewCycleAndUser(cycle, employee))
                .thenReturn(Optional.empty());

        assertNull(service.getPublishedScorecard("Q3-2026", 1L));
    }

    @Test
    void getScorecardsForUser_shouldReturnHistoryOldestFirst() {
        service.getScorecardsForUser(1L);

        // Ordering has to come from the database. findByUser returns rows in
        // whatever order they arrive, which is chronological often enough to
        // pass a manual check and wrong as soon as a card is regenerated.
        verify(scorecardRepository).findByUserOrderByReviewCycleStartDateAsc(employee);
    }
}

