package com.example.kpi.controller;

import com.example.kpi.dto.CalibrationRequest;
import com.example.kpi.dto.KpiScorecardDto;
import com.example.kpi.entity.KpiScorecard;
import com.example.kpi.entity.User;
import com.example.kpi.service.KpiReviewWorkflowService;
import com.example.kpi.service.KpiScoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for KPI scorecards, calibration and cycle reporting.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class KpiScorecardController {

    private final KpiScoringService scoringService;
    private final KpiReviewWorkflowService workflowService;

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;
        return workflowService.findAuthenticatedUserByEmail(email);
    }

    /** Generate (or refresh) scorecards for every employee in a cycle. */
    @PostMapping("/api/kpi/scorecards/generate")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> generateForCycle(@RequestParam String cycleCode) {
        scoringService.generateAllScorecards(cycleCode);
        List<KpiScorecard> generated = scoringService.getScorecardsForCycle(cycleCode);
        return ResponseEntity.ok(Map.of(
                "cycleCode", cycleCode,
                "scorecards", generated.size()
        ));
    }

    /** Generate (or refresh) a single employee's scorecard. */
    @PostMapping("/api/kpi/scorecards/generate/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Void> generateForUser(@RequestParam String cycleCode, @PathVariable Long userId) {
        scoringService.generateScorecard(cycleCode, userId);
        return ResponseEntity.accepted().build();
    }

    /** All scorecards in a cycle. */
    @GetMapping("/api/kpi/scorecards")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<KpiScorecardDto>> getForCycle(@RequestParam String cycleCode) {
        return ResponseEntity.ok(scoringService.getScorecardsForCycle(cycleCode).stream()
                .map(KpiScorecardDto::from)
                .collect(Collectors.toList()));
    }

    /** The signed-in employee's own scorecard for a cycle, once published. */
    @GetMapping("/api/kpi/scorecards/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<KpiScorecardDto> getMine(@RequestParam String cycleCode) {
        User me = currentUser();
        KpiScorecard scorecard = scoringService.getPublishedScorecard(cycleCode, me.getId());
        return scorecard == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(KpiScorecardDto.from(scorecard));
    }

    /** The signed-in employee's published scorecard history, oldest cycle first. */
    @GetMapping("/api/kpi/scorecards/me/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiScorecardDto>> getMyHistory() {
        User me = currentUser();
        return ResponseEntity.ok(scoringService.getPublishedScorecardsForUser(me.getId()).stream()
                .map(KpiScorecardDto::from)
                .collect(Collectors.toList()));
    }

    /** Aggregate statistics for a cycle (distribution, averages, min/max). */
    @GetMapping("/api/kpi/scorecards/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> getSummary(@RequestParam String cycleCode) {
        return ResponseEntity.ok(scoringService.getCycleSummary(cycleCode));
    }

    /** Apply a calibration adjustment to a scorecard. */
    @PostMapping("/api/kpi/scorecards/{scorecardId}/calibrate")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<KpiScorecardDto> calibrate(
            @PathVariable Long scorecardId,
            @Valid @RequestBody CalibrationRequest request) {
        KpiScorecard calibrated = scoringService.calibrateScorecard(
                scorecardId,
                request.getOverallScore(),
                request.getNotes(),
                request.resolveStatus(),
                currentUser());
        return ResponseEntity.ok(KpiScorecardDto.from(calibrated));
    }

    /** Server-side CSV export of a full cycle. */
    @GetMapping("/api/kpi/scorecards/export")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> exportCycle(@RequestParam String cycleCode) {
        List<KpiScorecard> scorecards = scoringService.getScorecardsForCycle(cycleCode);

        StringBuilder csv = new StringBuilder();
        csv.append("Employee,Role,Level,Overall Score,Max Score,Status,Calibrated By,Calibration Notes\n");
        for (KpiScorecard s : scorecards) {
            csv.append(csvCell(s.getEmployeeName())).append(',')
               .append(csvCell(s.getRole())).append(',')
               .append(csvCell(s.getEmployeeLevel())).append(',')
               .append(csvCell(s.getOverallScore())).append(',')
               .append(csvCell(s.getMaxScore())).append(',')
               .append(csvCell(s.getStatus())).append(',')
               .append(csvCell(s.getCalibratedBy() != null ? s.getCalibratedBy().getFullName() : "")).append(',')
               .append(csvCell(s.getCalibrationNotes()))
               .append('\n');
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "scorecards-" + cycleCode + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    /** RFC-4180 style escaping so commas/quotes in names cannot break the file. */
    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}

