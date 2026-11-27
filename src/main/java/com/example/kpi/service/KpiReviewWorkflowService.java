package com.example.kpi.service;

import com.example.kpi.dto.BulkManagerReviewRequest;
import com.example.kpi.dto.BulkReviewResultDto;
import com.example.kpi.dto.EvidenceCreateRequest;
import com.example.kpi.dto.ManagerReviewRequest;
import com.example.kpi.dto.SelfAssessmentRequest;
import com.example.kpi.dto.TeamAssignmentFlowDto;
import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.KpiEvidence;
import com.example.kpi.entity.KpiStatusHistory;
import com.example.kpi.entity.Role;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.User;
import com.example.kpi.repository.KPIRepository;
import com.example.kpi.repository.KpiEvidenceRepository;
import com.example.kpi.repository.KpiStatusHistoryRepository;
import com.example.kpi.repository.ReviewCycleRepository;
import com.example.kpi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * KPI review workflow service containing business logic for self-assessment,
 * manager review and evidence operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KpiReviewWorkflowService {

    private final KPIRepository kpiRepository;
    private final KpiEvidenceRepository evidenceRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final UserRepository userRepository;
    private final KpiScoringService scoringService;
    private final NotificationService notificationService;
    private final KpiStatusHistoryRepository statusHistoryRepository;

    /**
     * Org-hierarchy scope resolution. Optional so plain unit tests that construct
     * this service directly still work; when absent only the reporting line applies.
     */
    @Autowired(required = false)
    private AccessControlService accessControlService;

    /**
     * Self-reference used to route internal calls through the transactional proxy.
     * {@code @Lazy} breaks the constructor cycle; null in plain unit tests.
     */
    @Autowired
    @Lazy
    private KpiReviewWorkflowService self;

    @Value("${app.evidence.upload-dir:uploads/kpi-evidence}")
    private String evidenceUploadDir;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.APPLICATION_PDF_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE
    );

    /** Statuses that sit in a reviewer's queue. */
    private static final Set<Kpi.KpiStatus> REVIEWABLE_STATUSES =
            Set.of(Kpi.KpiStatus.SUBMITTED, Kpi.KpiStatus.UNDER_REVIEW);

    /** Statuses an employee is allowed to submit (or re-submit) from. */
    private static final Set<Kpi.KpiStatus> SUBMITTABLE_STATUSES =
            Set.of(Kpi.KpiStatus.PENDING, Kpi.KpiStatus.REWORK_REQUIRED, Kpi.KpiStatus.REJECTED);

    /** Cycle states in which the appraisal workflow accepts changes. */
    private static final Set<ReviewCycle.CycleStatus> ACTIVE_CYCLE_STATUSES = Set.of(
            ReviewCycle.CycleStatus.OPEN,
            ReviewCycle.CycleStatus.IN_PROGRESS,
            ReviewCycle.CycleStatus.UNDER_REVIEW
    );

    public User findAuthenticatedUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    public List<Kpi> getMyKpis(String cycleCode, Long userId) {
        ReviewCycle cycle = resolveCycle(cycleCode);
        User user = resolveUser(userId);
        return kpiRepository.findByReviewCycleAndUser(cycle, user);
    }

    public List<Kpi> getEmployeeKpis(String cycleCode, Long userId, User reviewer) {
        ReviewCycle cycle = resolveCycle(cycleCode);
        User employee = resolveUser(userId);
        if (!canReviewEmployee(reviewer, employee)) {
            throw new SecurityException("You are not allowed to review this employee");
        }
        return kpiRepository.findByReviewCycleAndUser(cycle, employee);
    }

    public List<Kpi> getCycleKpis(String cycleCode) {
        ReviewCycle cycle = resolveCycle(cycleCode);
        return kpiRepository.findByReviewCycle(cycle);
    }

    /**
     * Cycle KPIs scoped to the caller. Admins see everything; a manager sees only
     * their own KPIs plus those of people they can review, rather than the whole
     * organisation's appraisal data.
     */
    @Transactional(readOnly = true)
    public List<Kpi> getCycleKpis(String cycleCode, User requester) {
        ReviewCycle cycle = resolveCycle(cycleCode);
        List<Kpi> all = kpiRepository.findByReviewCycle(cycle);
        if (isAdminReviewer(requester)) {
            return all;
        }
        return all.stream()
                .filter(Objects::nonNull)
                .filter(kpi -> canAccessKpi(requester, kpi))
                .toList();
    }

    /** All KPIs for the current user across every cycle (history view). */
    @Transactional(readOnly = true)
    public List<Kpi> getAllMyKpis(Long userId) {
        User user = resolveUser(userId);
        return kpiRepository.findByUserOrderByIdDesc(user);
    }

    public List<Kpi> getPendingReviews(User reviewer) {
        return getPendingReviews(reviewer, Pageable.unpaged()).getContent();
    }

    /**
     * Reviewer queue, scoped in SQL. Previously this loaded every SUBMITTED KPI in
     * the organisation and filtered in memory.
     */
    @Transactional(readOnly = true)
    public Page<Kpi> getPendingReviews(User reviewer, Pageable pageable) {
        if (reviewer == null) {
            throw new SecurityException("Reviewer is required");
        }
        Pageable effective = pageable == null ? Pageable.unpaged() : pageable;
        if (isAdminReviewer(reviewer)) {
            return kpiRepository.findByStatusIn(REVIEWABLE_STATUSES, effective);
        }

        // Widen the queue with org-structure scope so squad/portfolio/tribe leads
        // see their people, not just direct reports.
        Set<Long> scopedUserIds = accessControlService == null
                ? Set.of()
                : accessControlService.accessibleUserIds(reviewer);
        if (scopedUserIds.isEmpty()) {
            // `IN ()` is invalid in JPQL, so fall back to the reporting-line query.
            return kpiRepository.findPendingForReviewer(reviewer.getId(), REVIEWABLE_STATUSES, effective);
        }
        return kpiRepository.findPendingForReviewerInScope(
                reviewer.getId(), scopedUserIds, REVIEWABLE_STATUSES, effective);
    }

    public Kpi submitSelfAssessment(Long kpiId, User currentUser, SelfAssessmentRequest request) {
        Kpi kpi = resolveKpi(kpiId);
        if (!kpi.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("KPI is not assigned to the current user");
        }

        requireActiveCycle(kpi, "self-assessment");

        Kpi.KpiStatus previous = kpi.getStatus();
        if (previous != null && !SUBMITTABLE_STATUSES.contains(previous)) {
            throw new IllegalStateException(
                    "KPI cannot be submitted while it is " + previous + ". Only PENDING, REWORK_REQUIRED "
                            + "or REJECTED KPIs can be submitted.");
        }

        if (request.getActualValue() != null) {
            kpi.setActualValue(request.getActualValue());
        }
        if (request.getComments() != null) {
            kpi.setComments(request.getComments());
        }
        if (request.getJustification() != null) {
            kpi.setJustification(request.getJustification());
        }

        if (kpi.getActualValue() == null) {
            throw new IllegalArgumentException("actualValue is required to submit a self-assessment");
        }
        if (kpi.getActualValue() < 0) {
            throw new IllegalArgumentException("actualValue cannot be negative");
        }

        // A resubmission must not carry the previous reviewer's verdict forward.
        kpi.setRating(null);
        kpi.setReviewerComments(null);
        kpi.setReviewedAt(null);
        kpi.setStatus(Kpi.KpiStatus.SUBMITTED);
        kpi.setSubmittedAt(LocalDateTime.now());

        Kpi saved = kpiRepository.save(kpi);
        recordTransition(saved, previous, Kpi.KpiStatus.SUBMITTED, currentUser, null,
                request.getComments());

        User reviewer = saved.getReviewer() != null ? saved.getReviewer() : saved.getUser().getReportingManager();
        if (reviewer != null && reviewer.getId() != null && !reviewer.getId().equals(currentUser.getId())) {
            notificationService.create(
                    reviewer,
                    "KPI submitted for review",
                    saved.getUser().getFullName() + " submitted KPI \"" + saved.getKpiName() + "\".",
                    "KPI_SUBMITTED",
                    "KPI",
                    saved.getId()
            );
        }
        return saved;
    }

    public Kpi submitManagerReview(Long kpiId, User reviewer, ManagerReviewRequest request) {
        Kpi kpi = resolveKpi(kpiId);
        if (!canReviewEmployee(reviewer, kpi.getUser())) {
            throw new SecurityException("You are not allowed to review this KPI");
        }

        requireActiveCycle(kpi, "review");

        ManagerReviewRequest.Decision decision = request.resolveDecision();
        Kpi.KpiStatus previous = kpi.getStatus();
        if (previous != null && !REVIEWABLE_STATUSES.contains(previous)) {
            throw new IllegalStateException(
                    "KPI cannot be reviewed while it is " + previous + ". Only SUBMITTED or UNDER_REVIEW "
                            + "KPIs can be reviewed.");
        }

        Kpi.KpiStatus target = switch (decision) {
            case APPROVE -> Kpi.KpiStatus.APPROVED;
            case REWORK -> Kpi.KpiStatus.REWORK_REQUIRED;
            case REJECT -> Kpi.KpiStatus.REJECTED;
        };

        // Sending work back without saying why is not actionable for the employee.
        if (decision != ManagerReviewRequest.Decision.APPROVE
                && (request.getReviewerComments() == null || request.getReviewerComments().isBlank())) {
            throw new IllegalArgumentException(
                    "reviewerComments are required when the decision is " + decision);
        }

        scoringService.applyDecision(kpiId, request.getRating(), request.getReviewerComments(), reviewer, target);
        Kpi rated = resolveKpi(kpiId);
        recordTransition(rated, previous, target, reviewer, request.getRating(), request.getReviewerComments());

        if (rated.getUser() != null && rated.getUser().getId() != null && !rated.getUser().getId().equals(reviewer.getId())) {
            notificationService.create(
                    rated.getUser(),
                    notificationTitleFor(decision),
                    notificationBodyFor(decision, reviewer, rated, request),
                    notificationTypeFor(decision),
                    "KPI",
                    rated.getId()
            );
        }
        return rated;
    }

    /**
     * Apply one decision to many KPIs. A failure on a single KPI is collected and
     * reported rather than rolling back the whole batch.
     *
     * <p>Not itself transactional: each item opens its own transaction so that a
     * rollback on one KPI cannot mark the batch's transaction rollback-only.</p>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BulkReviewResultDto submitBulkManagerReview(BulkManagerReviewRequest request, User reviewer) {
        List<Long> kpiIds = request.getKpiIds() == null ? List.of() : request.getKpiIds();
        List<BulkReviewResultDto.Failure> failures = new ArrayList<>();
        int succeeded = 0;

        for (Long kpiId : kpiIds) {
            try {
                self().submitBulkItem(kpiId, reviewer, request.getReview());
                succeeded++;
            } catch (Exception ex) {
                log.warn("Bulk review failed for KPI {}: {}", kpiId, ex.getMessage());
                failures.add(new BulkReviewResultDto.Failure(kpiId, ex.getMessage()));
            }
        }

        return new BulkReviewResultDto(kpiIds.size(), succeeded, failures.size(), failures);
    }

    /**
     * Each item runs in its own transaction so one bad KPI cannot poison the batch.
     * Must be invoked through the proxy — see {@link #self()}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitBulkItem(Long kpiId, User reviewer, ManagerReviewRequest review) {
        submitManagerReview(kpiId, reviewer, review);
    }

    /**
     * The Spring proxy for this bean, so {@code @Transactional} on an internal call
     * is honoured. Falls back to {@code this} when running outside a container
     * (plain unit tests).
     */
    private KpiReviewWorkflowService self() {
        return self != null ? self : this;
    }

    @Transactional(readOnly = true)
    public List<KpiStatusHistory> getStatusHistory(Long kpiId, User requester) {
        Kpi kpi = resolveKpi(kpiId);
        if (!canAccessKpi(requester, kpi)) {
            throw new SecurityException("You are not allowed to view this KPI's history");
        }
        return statusHistoryRepository.findByKpiOrderByChangedAtAsc(kpi);
    }


    private void recordTransition(Kpi kpi, Kpi.KpiStatus from, Kpi.KpiStatus to, User actor,
                                  Double rating, String note) {
        KpiStatusHistory history = new KpiStatusHistory();
        history.setKpi(kpi);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedBy(actor);
        history.setChangedAt(LocalDateTime.now());
        history.setRating(rating);
        history.setNote(note);
        statusHistoryRepository.save(history);
    }

    private void requireActiveCycle(Kpi kpi, String action) {
        ReviewCycle cycle = kpi.getReviewCycle();
        if (cycle == null || cycle.getStatus() == null) {
            return;
        }
        if (!ACTIVE_CYCLE_STATUSES.contains(cycle.getStatus())) {
            throw new IllegalStateException(
                    "Review cycle " + cycle.getCycleCode() + " is " + cycle.getStatus()
                            + "; " + action + " is no longer accepted.");
        }
    }

    private String notificationTitleFor(ManagerReviewRequest.Decision decision) {
        return switch (decision) {
            case APPROVE -> "KPI approved";
            case REWORK -> "KPI sent back for rework";
            case REJECT -> "KPI rejected";
        };
    }

    private String notificationTypeFor(ManagerReviewRequest.Decision decision) {
        return switch (decision) {
            case APPROVE -> "KPI_REVIEWED";
            case REWORK -> "KPI_REWORK_REQUIRED";
            case REJECT -> "KPI_REJECTED";
        };
    }

    private String notificationBodyFor(ManagerReviewRequest.Decision decision, User reviewer, Kpi kpi,
                                       ManagerReviewRequest request) {
        String who = reviewer.getFullName();
        String what = "\"" + kpi.getKpiName() + "\"";
        return switch (decision) {
            case APPROVE -> who + " approved KPI " + what
                    + (request.getRating() != null ? " with rating " + request.getRating() + "/5." : ".");
            case REWORK -> who + " requested rework on KPI " + what + ": " + request.getReviewerComments();
            case REJECT -> who + " rejected KPI " + what + ": " + request.getReviewerComments();
        };
    }

    @Transactional(readOnly = true)
    public List<TeamAssignmentFlowDto> getTeamAssignmentFlow(User reviewer) {
        List<Kpi> pending = getPendingReviews(reviewer);
        Map<String, TransitionAgg> grouped = new LinkedHashMap<>();

        for (Kpi kpi : pending) {
            if (kpi == null || kpi.getUser() == null) {
                continue;
            }
            Role from = pickPrimaryRole(kpi.getUser());
            User targetReviewer = kpi.getReviewer() != null ? kpi.getReviewer() : (kpi.getUser().getReportingManager() != null ? kpi.getUser().getReportingManager() : reviewer);
            Role to = pickPrimaryRole(targetReviewer);

            String fromCode = normalizeRoleCode(from);
            String toCode = normalizeRoleCode(to);
            String key = fromCode + "->" + toCode;

            TransitionAgg agg = grouped.computeIfAbsent(key, k -> new TransitionAgg(from, to));
            agg.kpiIds.add(kpi.getId());

            Long employeeId = kpi.getUser().getId();
            UserRequestAgg req = agg.requests.computeIfAbsent(employeeId, id -> new UserRequestAgg(kpi.getUser(), targetReviewer));
            req.kpiCount++;
            if (kpi.getReviewCycle() != null && kpi.getReviewCycle().getCycleCode() != null) {
                req.cycleCodes.add(kpi.getReviewCycle().getCycleCode());
            }
            if (kpi.getStatus() != null) {
                req.statuses.add(kpi.getStatus().name());
            }
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    TransitionAgg agg = entry.getValue();
                    TeamAssignmentFlowDto dto = new TeamAssignmentFlowDto();
                    dto.setTransitionKey(entry.getKey());
                    dto.setFromRole(new TeamAssignmentFlowDto.RoleRef(normalizeRoleCode(agg.fromRole), normalizeRoleName(agg.fromRole)));
                    dto.setToRole(new TeamAssignmentFlowDto.RoleRef(normalizeRoleCode(agg.toRole), normalizeRoleName(agg.toRole)));
                    dto.setPendingKpis(agg.kpiIds.size());
                    dto.setPendingUsers(agg.requests.size());

                    List<TeamAssignmentFlowDto.UserRequestSummary> requests = agg.requests.values().stream()
                            .sorted(Comparator.comparing((UserRequestAgg r) -> r.kpiCount).reversed()
                                    .thenComparing(r -> r.user.getFullName(), String.CASE_INSENSITIVE_ORDER))
                            .map(r -> new TeamAssignmentFlowDto.UserRequestSummary(
                                    r.user.getId(),
                                    r.user.getFullName(),
                                    r.user.getEmail(),
                                    r.kpiCount,
                                    String.join(", ", r.cycleCodes),
                                    String.join(", ", r.statuses),
                                    r.reviewer != null ? r.reviewer.getId() : null,
                                    r.reviewer != null ? r.reviewer.getFullName() : "Unassigned"
                            ))
                            .toList();
                    dto.setRequests(requests);
                    return dto;
                })
                .sorted(Comparator.comparing(TeamAssignmentFlowDto::getPendingUsers).reversed()
                        .thenComparing(TeamAssignmentFlowDto::getPendingKpis).reversed())
                .toList();
    }

    private boolean canReviewEmployee(User reviewer, User employee) {
        if (reviewer == null || employee == null) {
            return false;
        }
        if (isAdminReviewer(reviewer)) {
            return true;
        }
        // Org-structure scope (squad/portfolio/tribe) in addition to the direct
        // reporting line, so a Squad Lead reaches their Tech Focal and a Tribe
        // Lead reaches everyone beneath them.
        if (accessControlService != null && accessControlService.canReviewEmployee(reviewer, employee)) {
            return true;
        }
        User manager = employee.getReportingManager();
        return manager != null && manager.getId() != null && manager.getId().equals(reviewer.getId());
    }

    /**
     * Whether {@code user} may read or attach evidence for {@code kpi}.
     *
     * <p>Permitted for the KPI owner, the assigned reviewer, the owner's reporting
     * manager, and admins. Without this check any authenticated user could read a
     * colleague's appraisal evidence by guessing a KPI id.</p>
     */
    private boolean canAccessKpi(User user, Kpi kpi) {
        if (user == null || kpi == null) {
            return false;
        }
        if (isAdminReviewer(user)) {
            return true;
        }
        User owner = kpi.getUser();
        if (owner != null && owner.getId() != null && owner.getId().equals(user.getId())) {
            return true;
        }
        User assignedReviewer = kpi.getReviewer();
        if (assignedReviewer != null && assignedReviewer.getId() != null
                && assignedReviewer.getId().equals(user.getId())) {
            return true;
        }
        return canReviewEmployee(user, owner);
    }

    /**
     * Loads a KPI the requester is entitled to see.
     *
     * <p>Public so metric collection reuses this access rule rather than
     * growing a second, divergent one. The message names evidence because that
     * was the original caller; the check itself is general.</p>
     */
    public Kpi resolveAccessibleKpi(Long kpiId, User user) {
        Kpi kpi = resolveKpi(kpiId);
        if (!canAccessKpi(user, kpi)) {
            throw new SecurityException("You are not allowed to access this KPI");
        }
        return kpi;
    }

    private boolean isAdminReviewer(User reviewer) {
        if (reviewer.getRoles() == null) {
            return false;
        }
        return reviewer.getRoles().stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> {
                    String name = role.getRoleName() == null ? "" : role.getRoleName().toUpperCase(Locale.ROOT);
                    String code = role.getRoleCode() == null ? "" : role.getRoleCode().toUpperCase(Locale.ROOT);
                    return "ADMIN".equals(name) || "ROLE_ADMIN".equals(name) || "SYSTEM_ADMINISTRATOR".equals(name)
                            || "ADMIN".equals(code) || "ROLE_ADMIN".equals(code) || "SYSTEM_ADMINISTRATOR".equals(code);
                });
    }

    private Role pickPrimaryRole(User user) {
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            return null;
        }
        return user.getRoles().stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(this::roleDepth).reversed()
                        .thenComparing(r -> normalizeRoleName(r), String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    private int roleDepth(Role role) {
        int depth = 0;
        Role cursor = role;
        while (cursor != null && cursor.getParentRole() != null) {
            depth++;
            cursor = cursor.getParentRole();
        }
        return depth;
    }

    private String normalizeRoleCode(Role role) {
        if (role == null) {
            return "UNASSIGNED";
        }
        String code = role.getRoleCode() != null && !role.getRoleCode().isBlank() ? role.getRoleCode() : role.getRoleName();
        if (code == null || code.isBlank()) {
            return "UNASSIGNED";
        }
        return code.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizeRoleName(Role role) {
        if (role == null) {
            return "Unassigned";
        }
        String name = role.getRoleName();
        if (name == null || name.isBlank()) {
            return normalizeRoleCode(role);
        }
        return name.trim();
    }

    private static class TransitionAgg {
        private final Role fromRole;
        private final Role toRole;
        private final Set<Long> kpiIds = new LinkedHashSet<>();
        private final Map<Long, UserRequestAgg> requests = new HashMap<>();

        private TransitionAgg(Role fromRole, Role toRole) {
            this.fromRole = fromRole;
            this.toRole = toRole;
        }
    }

    private static class UserRequestAgg {
        private final User user;
        private final User reviewer;
        private int kpiCount = 0;
        private final Set<String> cycleCodes = new LinkedHashSet<>();
        private final Set<String> statuses = new LinkedHashSet<>();

        private UserRequestAgg(User user, User reviewer) {
            this.user = user;
            this.reviewer = reviewer;
        }
    }

    public KpiEvidence addEvidence(Long kpiId, User uploader, EvidenceCreateRequest request) {
        Kpi kpi = resolveAccessibleKpi(kpiId, uploader);

        KpiEvidence evidence = new KpiEvidence();
        evidence.setKpi(kpi);
        evidence.setTitle(request.getTitle());
        evidence.setDescription(request.getDescription());
        evidence.setContentUrl(request.getContentUrl());
        evidence.setEvidenceType(request.getEvidenceType() == null ? "URL" : request.getEvidenceType());
        evidence.setFileType(request.getFileType() == null ? "URL" : request.getFileType());
        evidence.setFileSizeBytes(request.getFileSizeBytes() == null ? 0L : request.getFileSizeBytes());
        evidence.setUploadedAt(LocalDateTime.now());
        evidence.setUploadedBy(uploader);
        return evidenceRepository.save(evidence);
    }

    @Transactional(readOnly = true)
    public List<KpiEvidence> getEvidence(Long kpiId, User requester) {
        Kpi kpi = resolveAccessibleKpi(kpiId, requester);
        return evidenceRepository.findByKpi(kpi);
    }

    /**
     * Remove a piece of evidence. Only the uploader, the KPI owner or an admin may
     * do so, and not once the KPI has been approved.
     */
    public void deleteEvidence(Long evidenceId, User requester) {
        KpiEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));

        Kpi kpi = evidence.getKpi();
        boolean isUploader = evidence.getUploadedBy() != null
                && evidence.getUploadedBy().getId() != null
                && evidence.getUploadedBy().getId().equals(requester.getId());
        boolean isOwner = kpi != null && kpi.getUser() != null
                && kpi.getUser().getId().equals(requester.getId());

        if (!isUploader && !isOwner && !isAdminReviewer(requester)) {
            throw new SecurityException("You are not allowed to delete this evidence");
        }
        if (kpi != null && kpi.getStatus() == Kpi.KpiStatus.APPROVED && !isAdminReviewer(requester)) {
            throw new IllegalStateException("Evidence cannot be removed once the KPI is approved");
        }
        evidenceRepository.delete(evidence);
    }

    public KpiEvidence addEvidenceFile(Long kpiId, User uploader, MultipartFile file, String title, String description) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Attachment file is required");
        }

        String originalName = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
        String extension = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only PDF, JPG, JPEG, and PNG files are allowed");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: PDF, JPG, PNG");
        }

        Kpi kpi = resolveAccessibleKpi(kpiId, uploader);

        Path uploadRoot = Paths.get(evidenceUploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
            String storedFileName = UUID.randomUUID() + "_" + sanitizeFilename(originalName);
            Path target = uploadRoot.resolve(storedFileName).normalize();
            if (!target.startsWith(uploadRoot)) {
                throw new IllegalArgumentException("Invalid file path");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }

            KpiEvidence evidence = new KpiEvidence();
            evidence.setKpi(kpi);
            evidence.setTitle((title == null || title.isBlank()) ? originalName : title.trim());
            evidence.setDescription(description);
            evidence.setEvidenceType("FILE");
            evidence.setFileType(extension.toUpperCase(Locale.ROOT));
            evidence.setFileSizeBytes(file.getSize());
            evidence.setContentUrl("/api/kpi/review/evidence/file/" + storedFileName);
            evidence.setUploadedAt(LocalDateTime.now());
            evidence.setUploadedBy(uploader);
            return evidenceRepository.save(evidence);
        } catch (IOException e) {
            log.error("Failed to store evidence file for KPI {}", kpiId, e);
            throw new IllegalStateException("Failed to upload file");
        }
    }

    /**
     * Resolve an uploaded evidence file for download.
     *
     * <p>The stored name is looked up against its evidence row so the caller's
     * access to the parent KPI can be verified — a UUID in the filename is not an
     * access-control mechanism.</p>
     */
    @Transactional(readOnly = true)
    public Path resolveEvidenceFilePath(String storedFileName, User requester) {
        if (storedFileName == null || storedFileName.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }

        String contentUrl = "/api/kpi/review/evidence/file/" + storedFileName;
        KpiEvidence evidence = evidenceRepository.findByContentUrl(contentUrl)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        if (!canAccessKpi(requester, evidence.getKpi())) {
            throw new SecurityException("You are not allowed to access this file");
        }

        Path uploadRoot = Paths.get(evidenceUploadDir).toAbsolutePath().normalize();
        Path filePath = uploadRoot.resolve(storedFileName).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File not found");
        }
        return filePath;
    }

    private String sanitizeFilename(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private ReviewCycle resolveCycle(String cycleCode) {
        if (cycleCode == null || cycleCode.isBlank()) {
            throw new IllegalArgumentException("cycleCode is required");
        }
        return reviewCycleRepository.findByCycleCode(cycleCode)
                .orElseThrow(() -> new IllegalArgumentException("Review cycle not found: " + cycleCode));
    }

    private User resolveUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be a positive number");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private Kpi resolveKpi(Long kpiId) {
        if (kpiId == null || kpiId <= 0) {
            throw new IllegalArgumentException("kpiId must be a positive number");
        }
        return kpiRepository.findById(kpiId)
                .orElseThrow(() -> new IllegalArgumentException("KPI not found: " + kpiId));
    }
}

