package com.example.kpi.controller;

import com.example.kpi.dto.ApiResponse;
import com.example.kpi.dto.SquadRequest;
import com.example.kpi.dto.SquadResponse;
import com.example.kpi.service.SquadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/squads")
public class SquadController {

    private final SquadService squadService;

    public SquadController(SquadService squadService) {
        this.squadService = squadService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SquadResponse>>> getAll(@RequestParam(required = false) Long portfolioId) {
        List<SquadResponse> data = (portfolioId != null)
                ? squadService.getByPortfolio(portfolioId)
                : squadService.getAll();
        return ResponseEntity.ok(ApiResponse.success("Squads fetched successfully", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SquadResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Squad fetched successfully", squadService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SquadResponse>> create(@Valid @RequestBody SquadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Squad created successfully", squadService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SquadResponse>> update(@PathVariable Long id, @Valid @RequestBody SquadRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Squad updated successfully", squadService.update(id, request)));
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SquadResponse>> enable(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Squad activated", squadService.setActive(id, true)));
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SquadResponse>> disable(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Squad deactivated", squadService.setActive(id, false)));
    }

    @PutMapping("/{id}/leads")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SquadResponse>> assignLeads(@PathVariable Long id, @RequestBody List<Long> leadIds) {
        return ResponseEntity.ok(ApiResponse.success("Squad leads assigned", squadService.assignLeads(id, leadIds)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        squadService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Squad deleted successfully", "deleted"));
    }
}
