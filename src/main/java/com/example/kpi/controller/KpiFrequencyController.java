package com.example.kpi.controller;

import com.example.kpi.entity.KpiFrequency;
import com.example.kpi.repository.KpiMasterRepository;
import com.example.kpi.repository.KpiFrequencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for KPI Frequency management (admin only)
 */
@RestController
@RequestMapping("/api/kpi/frequencies")
@RequiredArgsConstructor
@Slf4j
public class KpiFrequencyController {

    private final KpiFrequencyRepository repository;
    private final KpiMasterRepository kpiMasterRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<KpiFrequency>> findAll() {
        log.debug("Fetching all KPI frequencies");
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiFrequency>> findActive() {
        log.debug("Fetching active KPI frequencies");
        return ResponseEntity.ok(repository.findByActiveOrderByDisplayOrder(true));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiFrequency> findById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiFrequency> create(@RequestBody KpiFrequency frequency) {
        log.info("Creating KPI frequency: {}", frequency.getFrequencyCode());
        try {
            if (frequency.getFrequencyName() == null || frequency.getFrequencyCode() == null) {
                return ResponseEntity.badRequest().build();
            }
            if (frequency.getDisplayOrder() == null) {
                frequency.setDisplayOrder(999);
            }
            if (frequency.getActive() == null) {
                frequency.setActive(true);
            }
            KpiFrequency saved = repository.save(frequency);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Error creating KPI frequency", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiFrequency> update(@PathVariable Long id, @RequestBody KpiFrequency request) {
        log.info("Updating KPI frequency: {}", id);
        try {
            KpiFrequency existing = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("KPI frequency not found: " + id));
            if (request.getFrequencyName() != null) existing.setFrequencyName(request.getFrequencyName());
            if (request.getDescription() != null) existing.setDescription(request.getDescription());
            if (request.getDisplayOrder() != null) existing.setDisplayOrder(request.getDisplayOrder());
            if (request.getActive() != null) existing.setActive(request.getActive());
            return ResponseEntity.ok(repository.save(existing));
        } catch (Exception e) {
            log.error("Error updating KPI frequency: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting KPI frequency: {}", id);
        try {
            KpiFrequency frequency = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("KPI frequency not found: " + id));
            long inUse = kpiMasterRepository.countByFrequency(frequency);
            if (inUse > 0) {
                log.warn("Cannot delete KPI frequency {}. In use by {} KPI masters", id, inUse);
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting KPI frequency: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

