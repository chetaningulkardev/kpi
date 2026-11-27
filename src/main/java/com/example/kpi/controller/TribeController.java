package com.example.kpi.controller;

import com.example.kpi.dto.ApiResponse;
import com.example.kpi.dto.TribeRequest;
import com.example.kpi.dto.TribeResponse;
import com.example.kpi.service.TribeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tribes")
public class TribeController {

    private final TribeService tribeService;

    public TribeController(TribeService tribeService) {
        this.tribeService = tribeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TribeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Tribes fetched successfully", tribeService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TribeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tribe fetched successfully", tribeService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TribeResponse>> create(@Valid @RequestBody TribeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tribe created successfully", tribeService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TribeResponse>> update(@PathVariable Long id, @Valid @RequestBody TribeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tribe updated successfully", tribeService.update(id, request)));
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TribeResponse>> enable(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tribe activated", tribeService.setActive(id, true)));
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TribeResponse>> disable(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tribe deactivated", tribeService.setActive(id, false)));
    }

    @PutMapping("/{id}/leads")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TribeResponse>> assignLeads(@PathVariable Long id, @RequestBody List<Long> leadIds) {
        return ResponseEntity.ok(ApiResponse.success("Tribe leads assigned", tribeService.assignLeads(id, leadIds)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        tribeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Tribe deleted successfully", "deleted"));
    }
}
