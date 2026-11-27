package com.example.kpi.controller;

import com.example.kpi.dto.ApiResponse;
import com.example.kpi.dto.PortfolioRequest;
import com.example.kpi.dto.PortfolioResponse;
import com.example.kpi.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PortfolioResponse>>> getAll(@RequestParam(required = false) Long tribeId) {
        List<PortfolioResponse> data = (tribeId != null)
                ? portfolioService.getByTribe(tribeId)
                : portfolioService.getAll();
        return ResponseEntity.ok(ApiResponse.success("Portfolios fetched successfully", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Portfolio fetched successfully", portfolioService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PortfolioResponse>> create(@Valid @RequestBody PortfolioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Portfolio created successfully", portfolioService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PortfolioResponse>> update(@PathVariable Long id, @Valid @RequestBody PortfolioRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Portfolio updated successfully", portfolioService.update(id, request)));
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PortfolioResponse>> enable(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Portfolio activated", portfolioService.setActive(id, true)));
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PortfolioResponse>> disable(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Portfolio deactivated", portfolioService.setActive(id, false)));
    }

    @PutMapping("/{id}/leads")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PortfolioResponse>> assignLeads(@PathVariable Long id, @RequestBody List<Long> leadIds) {
        return ResponseEntity.ok(ApiResponse.success("Portfolio leads assigned", portfolioService.assignLeads(id, leadIds)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        portfolioService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Portfolio deleted successfully", "deleted"));
    }
}
