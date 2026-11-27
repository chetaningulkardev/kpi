package com.example.kpi.controller;

import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.repository.ReviewCycleRepository;
import com.example.kpi.service.KpiAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@RestController
@RequestMapping("/api/config/review-cycles")
@RequiredArgsConstructor
@Slf4j
public class ReviewCycleConfigurationController {

    private final ReviewCycleRepository repository;
    private final KpiAssignmentService kpiAssignmentService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReviewCycle> findAll() {
        log.debug("Fetching all review cycles");
        return repository.findAll();
    }

    /**
     * User-facing list of cycles available for KPI workflows.
     * Keeps admin-only configuration endpoint restricted while allowing employees to load cycle filters.
     */
    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public List<ReviewCycle> findAvailableForUsers() {
        EnumSet<ReviewCycle.CycleStatus> visibleStatuses = EnumSet.of(
                ReviewCycle.CycleStatus.OPEN,
                ReviewCycle.CycleStatus.IN_PROGRESS,
                ReviewCycle.CycleStatus.UNDER_REVIEW,
                ReviewCycle.CycleStatus.CALIBRATION
        );

        return repository.findAll().stream()
                .filter(cycle -> cycle.getStatus() != null && visibleStatuses.contains(cycle.getStatus()))
                .sorted(Comparator.comparing(ReviewCycle::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewCycle> create(@RequestBody ReviewCycle cycle) {
        log.info("Creating new review cycle: {}", cycle.getCycleCode());
        try {
            if (cycle.getCycleCode() == null || cycle.getCycleCode().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (cycle.getCycleName() == null || cycle.getCycleName().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (cycle.getStartDate() == null || cycle.getEndDate() == null || cycle.getEndDate().isBefore(cycle.getStartDate())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (cycle.getStatus() == null) {
                cycle.setStatus(ReviewCycle.CycleStatus.DRAFT);
            }
            if (cycle.getKpisGenerated() == null) {
                cycle.setKpisGenerated(false);
            }
            ReviewCycle saved = repository.save(cycle);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Error creating review cycle", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewCycle> update(@PathVariable Long id, @RequestBody ReviewCycle request) {
        log.info("Updating review cycle: {}", id);
        try {
            ReviewCycle existing = repository.findById(id)
                    .orElseThrow(() -> {
                        log.error("Review cycle not found: {}", id);
                        return new IllegalArgumentException("Review cycle not found");
                    });

            if (request.getCycleName() != null && !request.getCycleName().isBlank()) {
                existing.setCycleName(request.getCycleName());
            }
            if (request.getDescription() != null) {
                existing.setDescription(request.getDescription());
            }
            if (request.getStartDate() != null) {
                existing.setStartDate(request.getStartDate());
            }
            if (request.getEndDate() != null) {
                existing.setEndDate(request.getEndDate());
            }
            if (existing.getStartDate() != null && existing.getEndDate() != null
                    && existing.getEndDate().isBefore(existing.getStartDate())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (request.getStatus() != null) {
                existing.setStatus(request.getStatus());
            }

            ReviewCycle updated = repository.save(existing);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error updating review cycle: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting review cycle: {}", id);
        try {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting review cycle: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewCycle> openCycle(@PathVariable Long id) {
        try {
            ReviewCycle cycle = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Review cycle not found"));

            if (cycle.getStatus() != ReviewCycle.CycleStatus.DRAFT) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            cycle.setStatus(ReviewCycle.CycleStatus.OPEN);
            return ResponseEntity.ok(repository.save(cycle));
        } catch (Exception e) {
            log.error("Error opening review cycle: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewCycle> closeCycle(@PathVariable Long id) {
        try {
            ReviewCycle cycle = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Review cycle not found"));

            if (cycle.getStatus() != ReviewCycle.CycleStatus.OPEN
                    && cycle.getStatus() != ReviewCycle.CycleStatus.IN_PROGRESS
                    && cycle.getStatus() != ReviewCycle.CycleStatus.UNDER_REVIEW
                    && cycle.getStatus() != ReviewCycle.CycleStatus.CALIBRATION) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            cycle.setStatus(ReviewCycle.CycleStatus.CLOSED);
            return ResponseEntity.ok(repository.save(cycle));
        } catch (Exception e) {
            log.error("Error closing review cycle: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/{id}/generate-kpis")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> generateKpis(@PathVariable Long id) {
        try {
            ReviewCycle cycle = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Review cycle not found"));

            if (cycle.getStatus() != ReviewCycle.CycleStatus.OPEN
                    && cycle.getStatus() != ReviewCycle.CycleStatus.IN_PROGRESS) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("KPI generation allowed only in OPEN or IN_PROGRESS cycle");
            }

            com.example.kpi.service.KpiGenerationService.GenerationSummary summary =
                    kpiAssignmentService.generateAssignments(cycle.getCycleCode());
            cycle.setKpisGenerated(summary.getCreatedKpis() > 0 || Boolean.TRUE.equals(cycle.getKpisGenerated()));
            repository.save(cycle);

            String status = summary.hasFailures() ? "PARTIAL_SUCCESS" : "SUCCESS";
            return ResponseEntity.ok("KPI generation " + status
                    + ". Created " + summary.getCreatedKpis()
                    + " KPI records (processed " + summary.getProcessedUsers()
                    + ", skipped " + summary.getSkippedUsers()
                    + ", failed " + summary.getFailedUsers() + ").");
        } catch (Exception e) {
            log.error("Error generating KPIs for cycle: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
