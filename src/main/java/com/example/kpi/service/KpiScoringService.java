package com.example.kpi.service;

import com.example.kpi.entity.*;
import com.example.kpi.repository.ReviewCycleRepository;
import com.example.kpi.repository.UserRepository;
import com.example.kpi.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * KPI Scoring Service - Handles KPI rating, weighting, and score calculation
 */
@Service
@Slf4j
@Transactional
public class KpiScoringService {

    private final KPIRepository kpiRepository;
    private final KpiScorecardRepository scorecardRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final UserRepository userRepository;

    public KpiScoringService(
            KPIRepository kpiRepository,
            KpiScorecardRepository scorecardRepository,
            ReviewCycleRepository reviewCycleRepository,
            UserRepository userRepository) {
        this.kpiRepository = kpiRepository;
        this.scorecardRepository = scorecardRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.userRepository = userRepository;
    }

    /** Convenience: generate scorecard by cycleCode + userId */
    public void generateScorecard(String cycleCode, Long userId) {
        ReviewCycle cycle = reviewCycleRepository.findByCycleCode(cycleCode)
                .orElseThrow(() -> new IllegalArgumentException("Review cycle not found: " + cycleCode));
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        generateScorecard(employee, cycle, null);
    }

    /** Convenience: generate all scorecards by cycleCode */
    public void generateAllScorecards(String cycleCode) {
        ReviewCycle cycle = reviewCycleRepository.findByCycleCode(cycleCode)
                .orElseThrow(() -> new IllegalArgumentException("Review cycle not found: " + cycleCode));
        generateScorecardsForCycle(cycle);
    }

    @Transactional(readOnly = true)
    public List<KpiScorecard> getScorecardsForCycle(String cycleCode) {
        ReviewCycle cycle = reviewCycleRepository.findByCycleCode(cycleCode)
                .orElseThrow(() -> new IllegalArgumentException("Review cycle not found: " + cycleCode));
        return scorecardRepository.findByReviewCycle(cycle);
    }

    @Transactional(readOnly = true)
    public KpiScorecard getScorecard(String cycleCode, Long userId) {
        ReviewCycle cycle = reviewCycleRepository.findByCycleCode(cycleCode)
                .orElseThrow(() -> new IllegalArgumentException("Review cycle not found: " + cycleCode));
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return scorecardRepository.findByReviewCycleAndUser(cycle, employee).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<KpiScorecard> getScorecardsForUser(Long userId) {
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return scorecardRepository.findByUserOrderByReviewCycleStartDateAsc(employee);
    }

    /**
     * What the employee themselves is allowed to see.
     *
     * <p>Restricted to {@code PUBLISHED} on purpose. {@code DRAFT},
     * {@code UNDER_REVIEW} and {@code CALIBRATION} are working states, and
     * {@code FINAL} is still editable — {@link #calibrateScorecard} refuses
     * only once a card is published. Showing any of the others would let
     * someone read a rating that is still being moderated, anchor on it, and
     * then be told a different number is the real one. Calibration exists to
     * adjust scores across a cohort; that is only defensible if it happens
     * before the employee has seen the figure.
     */
    @Transactional(readOnly = true)
    public List<KpiScorecard> getPublishedScorecardsForUser(Long userId) {
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return scorecardRepository.findByUserAndStatusOrderByReviewCycleStartDateAsc(
                employee, KpiScorecard.ScorecardStatus.PUBLISHED);
    }

    /**
     * One published scorecard, or null when the cycle has not been released to
     * this employee yet. Null and "no such cycle" are deliberately the same
     * answer here: telling someone their scorecard exists but is being
     * calibrated invites exactly the premature conversation the gate prevents.
     */
    @Transactional(readOnly = true)
    public KpiScorecard getPublishedScorecard(String cycleCode, Long userId) {
        KpiScorecard scorecard = getScorecard(cycleCode, userId);
        return scorecard != null && scorecard.getStatus() == KpiScorecard.ScorecardStatus.PUBLISHED
                ? scorecard
                : null;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCycleSummary(String cycleCode) {
        ReviewCycle cycle = reviewCycleRepository.findByCycleCode(cycleCode)
                .orElseThrow(() -> new IllegalArgumentException("Review cycle not found: " + cycleCode));
        return getCycleSummary(cycle);
    }

    /**
     * Apply a calibration adjustment to a scorecard.
     *
     * <p>Calibration is how ratings get normalised across managers; the original
     * calculated score stays recoverable by regenerating the scorecard.</p>
     */
    public KpiScorecard calibrateScorecard(Long scorecardId, Double moderatedScore, String notes,
                                           String targetStatus, User calibrator) {
        if (calibrator == null) {
            throw new IllegalArgumentException("Calibrator cannot be null");
        }
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("Calibration notes are required");
        }

        KpiScorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new IllegalArgumentException("Scorecard not found: " + scorecardId));

        if (scorecard.getStatus() == KpiScorecard.ScorecardStatus.PUBLISHED) {
            throw new IllegalStateException("A published scorecard can no longer be calibrated");
        }

        if (moderatedScore != null) {
            if (moderatedScore < 0 || moderatedScore > 5.0) {
                throw new IllegalArgumentException("Moderated score must be between 0 and 5");
            }
            scorecard.setOverallScore(moderatedScore);
        }

        KpiScorecard.ScorecardStatus status;
        try {
            status = KpiScorecard.ScorecardStatus.valueOf(
                    targetStatus == null ? "CALIBRATION" : targetStatus);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "status must be one of CALIBRATION, FINAL, PUBLISHED (received: " + targetStatus + ")");
        }
        if (status != KpiScorecard.ScorecardStatus.CALIBRATION
                && status != KpiScorecard.ScorecardStatus.FINAL
                && status != KpiScorecard.ScorecardStatus.PUBLISHED) {
            throw new IllegalArgumentException("status must be one of CALIBRATION, FINAL, PUBLISHED");
        }

        scorecard.setCalibrationNotes(notes.trim());
        scorecard.setCalibratedBy(calibrator);
        scorecard.setStatus(status);

        log.info("Scorecard {} calibrated to {} by {}", scorecardId, status, calibrator.getId());
        return scorecardRepository.save(scorecard);
    }

    /**
     * Rate a KPI with manager feedback and move it to APPROVED.
     * Retained for callers that only ever approve.
     */
    public void rateKpi(Long kpiId, Double rating, String reviewerComments, User reviewer) {
        applyDecision(kpiId, rating, reviewerComments, reviewer, Kpi.KpiStatus.APPROVED);
    }

    /**
     * Apply a manager decision to a KPI.
     *
     * @param targetStatus APPROVED, REJECTED or REWORK_REQUIRED
     */
    public void applyDecision(Long kpiId, Double rating, String reviewerComments, User reviewer,
                              Kpi.KpiStatus targetStatus) {
        // Validate inputs
        if (kpiId == null || kpiId <= 0) {
            log.error("Invalid KPI ID: {}", kpiId);
            throw new IllegalArgumentException("KPI ID must be a positive number");
        }
        if (reviewer == null) {
            log.error("Reviewer cannot be null");
            throw new IllegalArgumentException("Reviewer cannot be null");
        }
        if (targetStatus == null) {
            throw new IllegalArgumentException("Target status cannot be null");
        }

        boolean approving = targetStatus == Kpi.KpiStatus.APPROVED;
        if (approving && rating == null) {
            log.error("Rating cannot be null when approving KPI {}", kpiId);
            throw new IllegalArgumentException("Rating is required to approve a KPI");
        }

        log.info("Applying decision {} to KPI: {} with rating: {} by reviewer: {}",
                targetStatus, kpiId, rating, reviewer.getId());

        Kpi kpi = kpiRepository.findById(kpiId)
                .orElseThrow(() -> {
                    log.error("KPI not found: {}", kpiId);
                    return new IllegalArgumentException("KPI not found with ID: " + kpiId);
                });

        // Validate rating is within bounds (1-5 scale) whenever one is supplied
        if (rating != null && (rating < 1.0 || rating > 5.0)) {
            log.error("Rating out of bounds: {}. Expected 1-5", rating);
            throw new IllegalArgumentException("Rating must be between 1 and 5, received: " + rating);
        }

        if (rating != null) {
            kpi.setRating(rating);
        } else if (!approving) {
            // Sending work back must not leave a stale score behind.
            kpi.setRating(null);
        }
        kpi.setReviewerComments(reviewerComments != null ? reviewerComments : "");
        kpi.setReviewer(reviewer);
        kpi.setStatus(targetStatus);
        kpi.setReviewedAt(java.time.LocalDateTime.now());

        kpiRepository.save(kpi);
        log.debug("KPI: {} moved to {} with score: {}", kpiId, targetStatus, kpi.getRating());
    }

    /**
     * Calculate weighted score for a KPI - with null checking
     * Formula: (Rating × Weight) / 100
     */
    public Double calculateWeightedScore(Kpi kpi) {
        if (kpi == null) {
            log.error("KPI is null in calculateWeightedScore");
            return null;
        }

        if (kpi.getRating() == null || kpi.getWeight() == null) {
            log.debug("Rating or Weight is null for KPI: {}", kpi.getId());
            return null;
        }

        // Validate weight is positive and <= 100
        if (kpi.getWeight() < 0 || kpi.getWeight() > 100) {
            log.warn("Invalid weight for KPI: {}. Weight: {}", kpi.getId(), kpi.getWeight());
            return 0.0;
        }

        Double weightedScore = (kpi.getRating() * kpi.getWeight()) / 100.0;
        log.debug("Calculated weighted score for KPI {}: rating={}, weight={}, score={}",
                  kpi.getId(), kpi.getRating(), kpi.getWeight(), weightedScore);
        return weightedScore;
    }

    /**
     * Generate (or refresh) the scorecard for an employee in a review cycle.
     *
     * @param template optional — a scorecard is not required to originate from a
     *                 template, and cycle-wide generation resolves it per user.
     */
    public KpiScorecard generateScorecard(User employee, ReviewCycle cycle, KpiTemplate template) {
        // Validate inputs
        if (employee == null) {
            log.error("Employee is null");
            throw new IllegalArgumentException("Employee cannot be null");
        }
        if (cycle == null) {
            log.error("Review cycle is null");
            throw new IllegalArgumentException("Review cycle cannot be null");
        }

        log.info("Generating scorecard for employee: {} in cycle: {}", employee.getId(), cycle.getId());

        // Get all KPIs for this employee in this cycle
        List<Kpi> kpis = kpiRepository.findByReviewCycleAndUser(cycle, employee);

        if (kpis == null || kpis.isEmpty()) {
            log.warn("No KPIs found for employee: {} in cycle: {}", employee.getId(), cycle.getId());
            return null;
        }

        // Filter out null KPIs
        kpis = new ArrayList<>(kpis);
        kpis.removeIf(Objects::isNull);

        if (kpis.isEmpty()) {
            log.warn("No valid KPIs found after filtering for employee: {}", employee.getId());
            return null;
        }

        // Fall back to the template attached to the employee's own KPIs.
        KpiTemplate resolvedTemplate = template;
        if (resolvedTemplate == null) {
            resolvedTemplate = kpis.stream()
                    .map(Kpi::getTemplate)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        // Refresh in place so repeated generation does not create duplicates.
        KpiScorecard scorecard = scorecardRepository.findByReviewCycleAndUser(cycle, employee)
                .orElseGet(() -> KpiScorecard.builder()
                        .reviewCycle(cycle)
                        .user(employee)
                        .build());

        scorecard.setTemplate(resolvedTemplate);
        scorecard.setEmployeeName(((employee.getFirstName() != null ? employee.getFirstName() : "") + " " +
                (employee.getLastName() != null ? employee.getLastName() : "")).trim());
        scorecard.setRole(employee.getRoles() != null && !employee.getRoles().isEmpty()
                ? employee.getRoles().stream()
                .filter(Objects::nonNull)
                .map(Role::getRoleName)
                .findFirst()
                .orElse("UNASSIGNED")
                : "UNASSIGNED");
        if (scorecard.getEmployeeLevel() == null) {
            scorecard.setEmployeeLevel("");
        }
        scorecard.setMaxScore(5.0);

        // Calculate category-wise scores
        Map<String, Double> categoryScores = new HashMap<>();

        for (Kpi kpi : kpis) {
            if (kpi == null || kpi.getCategoryName() == null) {
                continue;
            }

            String category = kpi.getCategoryName();
            Double weightedScore = calculateWeightedScore(kpi);

            if (weightedScore != null && !Double.isNaN(weightedScore) && !Double.isInfinite(weightedScore)) {
                categoryScores.merge(category, weightedScore, Double::sum);
            }
        }

        scorecard.setCategoryScores(categoryScores);

        // Overall score is the SUM of weighted scores: with weights totalling 100%,
        // sum(rating * weight / 100) is already the weighted-average rating.
        // Averaging them here would divide by the KPI count a second time.
        double overallScore = kpis.stream()
                .map(this::calculateWeightedScore)
                .filter(score -> score != null && !Double.isNaN(score) && !Double.isInfinite(score))
                .mapToDouble(Double::doubleValue)
                .sum();

        scorecard.setOverallScore(overallScore);
        log.debug("Generated scorecard for employee: {} with overall score: {}", employee.getId(), overallScore);

        return scorecardRepository.save(scorecard);
    }

    /**
     * Bulk generate scorecards for all employees in a review cycle
     */
    public void generateScorecardsForCycle(ReviewCycle cycle) {
        if (cycle == null) {
            log.error("Review cycle is null");
            throw new IllegalArgumentException("Review cycle cannot be null");
        }

        log.info("Generating scorecards for review cycle: {}", cycle.getId());

        List<Kpi> allKpis = kpiRepository.findByReviewCycle(cycle);
        if (allKpis == null || allKpis.isEmpty()) {
            log.warn("No KPIs found for review cycle: {}", cycle.getId());
            return;
        }

        // Get unique users from KPI records
        Set<User> uniqueUsers = new HashSet<>();
        for (Kpi kpi : allKpis) {
            if (kpi != null && kpi.getUser() != null) {
                uniqueUsers.add(kpi.getUser());
            }
        }

        log.info("Processing {} unique employees for scorecard generation", uniqueUsers.size());

        for (User employee : uniqueUsers) {
            try {
                generateScorecard(employee, cycle, null); // Template can be null, determined per user
            } catch (Exception e) {
                log.error("Error generating scorecard for employee: {} in cycle: {}",
                         employee.getId(), cycle.getId(), e);
            }
        }
    }

    /**
     * Get summary statistics for a review cycle - with comprehensive statistics
     */
    public Map<String, Object> getCycleSummary(ReviewCycle cycle) {
        if (cycle == null) {
            log.error("Review cycle is null");
            throw new IllegalArgumentException("Review cycle cannot be null");
        }

        List<KpiScorecard> scorecards = scorecardRepository.findByReviewCycle(cycle);
        if (scorecards == null) {
            scorecards = new ArrayList<>();
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalEmployees", scorecards.size());

        // Calculate average score
        Double averageScore = scorecards.stream()
                .filter(Objects::nonNull)
                .mapToDouble(s -> s.getOverallScore() != null ? s.getOverallScore() : 0.0)
                .average()
                .orElse(0.0);
        summary.put("averageScore", averageScore);

        // Calculate distribution by rating bands
        Map<String, Long> ratingDistribution = new HashMap<>();
        for (double i = 1; i <= 5; i++) {
            final double rating = i;
            long count = scorecards.stream()
                    .filter(Objects::nonNull)
                    .filter(s -> s.getOverallScore() != null &&
                               s.getOverallScore() >= rating &&
                               s.getOverallScore() < rating + 1)
                    .count();
            if (count > 0) {
                ratingDistribution.put((int)rating + " Star", count);
            }
        }
        summary.put("ratingDistribution", ratingDistribution);

        // Calculate status-wise distribution
        Map<String, Long> statusDistribution = new HashMap<>();
        for (KpiScorecard.ScorecardStatus status : KpiScorecard.ScorecardStatus.values()) {
            long count = scorecards.stream()
                    .filter(Objects::nonNull)
                    .filter(s -> s.getStatus() == status)
                    .count();
            if (count > 0) {
                statusDistribution.put(status.toString(), count);
            }
        }
        summary.put("statusDistribution", statusDistribution);

        // Calculate min and max scores
        scorecards.stream()
                .filter(Objects::nonNull)
                .mapToDouble(s -> s.getOverallScore() != null ? s.getOverallScore() : 0.0)
                .min()
                .ifPresent(min -> summary.put("minScore", min));

        scorecards.stream()
                .filter(Objects::nonNull)
                .mapToDouble(s -> s.getOverallScore() != null ? s.getOverallScore() : 0.0)
                .max()
                .ifPresent(max -> summary.put("maxScore", max));

        log.info("Generated cycle summary for cycle: {}. Total scorecards: {}, Average score: {}",
                cycle.getId(), scorecards.size(), averageScore);

        return summary;
    }
}

