package com.example.kpi.controller;

import com.example.kpi.dto.BulkManagerReviewRequest;
import com.example.kpi.dto.BulkReviewResultDto;
import com.example.kpi.dto.EvidenceCreateRequest;
import com.example.kpi.dto.KpiEvidenceViewDto;
import com.example.kpi.dto.KpiStatusHistoryDto;
import com.example.kpi.dto.KpiViewDto;
import com.example.kpi.dto.ManagerReviewRequest;
import com.example.kpi.dto.SelfAssessmentRequest;
import com.example.kpi.dto.TeamAssignmentFlowDto;
import com.example.kpi.entity.User;
import com.example.kpi.service.KpiReviewWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for KPI review workflow: self-assessment and manager review.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class KpiReviewController {

    private final KpiReviewWorkflowService workflowService;

    /** Current authenticated user */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;
        return workflowService.findAuthenticatedUserByEmail(email);
    }

    /** Get KPIs for the current employee; omit cycleCode for the full history */
    @GetMapping({"/api/kpi/review/my-kpis", "/api/kpi/my-kpis"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiViewDto>> getMyKpis(
            @RequestParam(required = false) String cycleCode) {
        User me = currentUser();
        List<com.example.kpi.entity.Kpi> kpis = (cycleCode == null || cycleCode.isBlank())
                ? workflowService.getAllMyKpis(me.getId())
                : workflowService.getMyKpis(cycleCode, me.getId());
        return ResponseEntity.ok(kpis.stream()
                .map(KpiViewDto::from)
                .collect(Collectors.toList()));
    }

    /** Get KPIs for a specific employee (manager/admin use) */
    @GetMapping("/api/kpi/review/employee-kpis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiViewDto>> getEmployeeKpis(
            @RequestParam String cycleCode,
            @RequestParam Long userId) {
        User reviewer = currentUser();
        return ResponseEntity.ok(workflowService.getEmployeeKpis(cycleCode, userId, reviewer).stream()
                .map(KpiViewDto::from)
                .collect(Collectors.toList()));
    }

    /** Get KPIs for a review cycle, scoped to what the caller may see */
    @GetMapping("/api/kpi/review/cycle-kpis")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<KpiViewDto>> getCycleKpis(@RequestParam String cycleCode) {
        User me = currentUser();
        return ResponseEntity.ok(workflowService.getCycleKpis(cycleCode, me).stream()
                .map(KpiViewDto::from)
                .collect(Collectors.toList()));
    }

    /** Get KPIs pending manager review */
    @GetMapping("/api/kpi/review/pending-reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiViewDto>> getPendingReviews() {
        User reviewer = currentUser();
        return ResponseEntity.ok(workflowService.getPendingReviews(reviewer).stream()
                .map(KpiViewDto::from)
                .collect(Collectors.toList()));
    }

    /** Paged variant of the reviewer queue, for large teams. */
    @GetMapping("/api/kpi/review/pending-reviews/page")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<KpiViewDto>> getPendingReviewsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        User reviewer = currentUser();
        int safeSize = Math.min(Math.max(1, size), 200);
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "submittedAt"));
        return ResponseEntity.ok(workflowService.getPendingReviews(reviewer, pageable).map(KpiViewDto::from));
    }

    /** Get pending review workload grouped by hierarchy transition (e.g. Developer -> Tech Focal) */
    @GetMapping("/api/kpi/review/team-assignment-flow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamAssignmentFlowDto>> getTeamAssignmentFlow() {
        User reviewer = currentUser();
        return ResponseEntity.ok(workflowService.getTeamAssignmentFlow(reviewer));
    }

    /** Employee submits self-assessment for a KPI */
    @PostMapping({"/api/kpi/review/self-assessment/{kpiId}", "/api/kpi/self-assess/{kpiId}"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<KpiViewDto> submitSelfAssessment(
            @PathVariable Long kpiId,
            @RequestBody SelfAssessmentRequest request) {
        User me = currentUser();
        return ResponseEntity.ok(KpiViewDto.from(workflowService.submitSelfAssessment(kpiId, me, request)));
    }

    /** Manager submits review/rating for a KPI (approve, rework or reject) */
    @PostMapping({"/api/kpi/review/manager-review/{kpiId}", "/api/kpi/review/{kpiId}"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<KpiViewDto> submitManagerReview(
            @PathVariable Long kpiId,
            @Valid @RequestBody ManagerReviewRequest request) {
        User reviewer = currentUser();
        return ResponseEntity.ok(KpiViewDto.from(workflowService.submitManagerReview(kpiId, reviewer, request)));
    }

    /** Apply one decision to many KPIs at once. */
    @PostMapping("/api/kpi/review/bulk-review")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BulkReviewResultDto> submitBulkManagerReview(
            @Valid @RequestBody BulkManagerReviewRequest request) {
        User reviewer = currentUser();
        return ResponseEntity.ok(workflowService.submitBulkManagerReview(request, reviewer));
    }

    /** Full status-transition audit trail for a KPI. */
    @GetMapping("/api/kpi/review/history/{kpiId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiStatusHistoryDto>> getStatusHistory(@PathVariable Long kpiId) {
        User me = currentUser();
        return ResponseEntity.ok(workflowService.getStatusHistory(kpiId, me).stream()
                .map(KpiStatusHistoryDto::from)
                .collect(Collectors.toList()));
    }

    /** Add evidence to a KPI */
    @PostMapping("/api/kpi/review/evidence/{kpiId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<KpiEvidenceViewDto> addEvidence(
            @PathVariable Long kpiId,
            @Valid @RequestBody EvidenceCreateRequest request) {
        User me = currentUser();
        return ResponseEntity.ok(KpiEvidenceViewDto.from(workflowService.addEvidence(kpiId, me, request)));
    }

    /** Upload evidence file (PDF/JPG/PNG) for a KPI */
    @PostMapping(value = "/api/kpi/review/evidence/upload/{kpiId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<KpiEvidenceViewDto> uploadEvidenceFile(
            @PathVariable Long kpiId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "title", required = false) String title,
            @RequestPart(value = "description", required = false) String description) {
        User me = currentUser();
        return ResponseEntity.ok(KpiEvidenceViewDto.from(
                workflowService.addEvidenceFile(kpiId, me, file, title, description)));
    }

    /** Get evidence for a KPI */
    @GetMapping("/api/kpi/review/evidence/{kpiId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiEvidenceViewDto>> getEvidence(@PathVariable Long kpiId) {
        User me = currentUser();
        return ResponseEntity.ok(workflowService.getEvidence(kpiId, me).stream()
                .map(KpiEvidenceViewDto::from)
                .collect(Collectors.toList()));
    }

    /** Remove a piece of evidence */
    @DeleteMapping("/api/kpi/review/evidence/item/{evidenceId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteEvidence(@PathVariable Long evidenceId) {
        workflowService.deleteEvidence(evidenceId, currentUser());
        return ResponseEntity.noContent().build();
    }

    /**
     * Download an uploaded KPI evidence file.
     *
     * <p>Served as an attachment, never inline. These bytes were supplied by a
     * user and are handed back from the application's own origin, so anything
     * the browser chooses to execute while rendering them runs alongside the
     * session that authorises every other request. A PDF is a program format —
     * it can carry JavaScript — and an image that is really markup is one
     * content-sniff away from the same outcome. Downloading it hands the file
     * to the operating system instead of to the page.
     *
     * <p>{@code nosniff} closes the other half: without it, a browser may
     * disregard the declared type and act on what the bytes look like.
     */
    @GetMapping("/api/kpi/review/evidence/file/{fileName:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadEvidenceFile(@PathVariable String fileName) throws MalformedURLException {
        Path filePath;
        filePath = workflowService.resolveEvidenceFilePath(fileName, currentUser());
        Resource resource = new UrlResource(filePath.toUri());
        String contentType = fileName.toLowerCase().endsWith(".pdf")
                ? MediaType.APPLICATION_PDF_VALUE
                : (fileName.toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filePath.getFileName().toString(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
