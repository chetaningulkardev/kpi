package com.example.kpi.controller;

import com.example.kpi.dto.ApiResponse;
import com.example.kpi.dto.AssignmentRequest;
import com.example.kpi.dto.AssignmentResponse;
import com.example.kpi.service.AssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Assignments fetched", assignmentService.getAllAssignments()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> getUserAssignments(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User assignments fetched", assignmentService.getUserAssignments(userId)));
    }

    // ---- Create ----
    @PostMapping("/tribe")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AssignmentResponse>> assignTribe(@RequestBody AssignmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User assigned to tribe", assignmentService.assignTribe(req)));
    }

    @PostMapping("/portfolio")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AssignmentResponse>> assignPortfolio(@RequestBody AssignmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User assigned to portfolio", assignmentService.assignPortfolio(req)));
    }

    @PostMapping("/squad")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AssignmentResponse>> assignSquad(@RequestBody AssignmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User assigned to squad", assignmentService.assignSquad(req)));
    }

    // ---- Update ----
    @PutMapping("/tribe/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AssignmentResponse>> updateTribe(@PathVariable Long id, @RequestBody AssignmentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Assignment updated", assignmentService.updateTribeAssignment(id, req)));
    }

    @PutMapping("/portfolio/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AssignmentResponse>> updatePortfolio(@PathVariable Long id, @RequestBody AssignmentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Assignment updated", assignmentService.updatePortfolioAssignment(id, req)));
    }

    @PutMapping("/squad/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AssignmentResponse>> updateSquad(@PathVariable Long id, @RequestBody AssignmentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Assignment updated", assignmentService.updateSquadAssignment(id, req)));
    }

    // ---- Remove ----
    @DeleteMapping("/tribe/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> removeTribe(@PathVariable Long id) {
        assignmentService.removeTribeAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("Assignment removed", "removed"));
    }

    @DeleteMapping("/portfolio/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> removePortfolio(@PathVariable Long id) {
        assignmentService.removePortfolioAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("Assignment removed", "removed"));
    }

    @DeleteMapping("/squad/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> removeSquad(@PathVariable Long id) {
        assignmentService.removeSquadAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("Assignment removed", "removed"));
    }

    // ---- Bulk set for a user (used by the user form) ----
    @PostMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> setUserAssignments(
            @PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        assignmentService.setUserAssignments(userId, body.get("tribeIds"), body.get("portfolioIds"), body.get("squadIds"));
        return ResponseEntity.ok(ApiResponse.success("User assignments updated", assignmentService.getUserAssignments(userId)));
    }
}
