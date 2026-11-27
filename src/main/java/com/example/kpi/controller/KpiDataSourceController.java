package com.example.kpi.controller;

import com.example.kpi.entity.KpiDataSource;
import com.example.kpi.repository.KpiMasterRepository;
import com.example.kpi.repository.KpiDataSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for KPI data source management (admin only).
 */
@RestController
@RequestMapping("/api/kpi/data-sources")
@RequiredArgsConstructor
@Slf4j
public class KpiDataSourceController {

    private final KpiDataSourceRepository repository;
    private final KpiMasterRepository kpiMasterRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<KpiDataSource>> findAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiDataSource>> findActive() {
        return ResponseEntity.ok(repository.findByActiveOrderByDisplayOrder(true));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiDataSource> findById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiDataSource> create(@RequestBody KpiDataSource source) {
        try {
            if (source.getSourceCode() == null || source.getSourceName() == null) {
                return ResponseEntity.badRequest().build();
            }
            if (source.getDisplayOrder() == null) {
                source.setDisplayOrder(999);
            }
            if (source.getActive() == null) {
                source.setActive(true);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(source));
        } catch (Exception e) {
            log.error("Error creating KPI data source", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiDataSource> update(@PathVariable Long id, @RequestBody KpiDataSource request) {
        try {
            KpiDataSource existing = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("KPI data source not found: " + id));
            if (request.getSourceName() != null) existing.setSourceName(request.getSourceName());
            if (request.getDescription() != null) existing.setDescription(request.getDescription());
            if (request.getDisplayOrder() != null) existing.setDisplayOrder(request.getDisplayOrder());
            if (request.getActive() != null) existing.setActive(request.getActive());
            return ResponseEntity.ok(repository.save(existing));
        } catch (Exception e) {
            log.error("Error updating KPI data source: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            KpiDataSource source = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("KPI data source not found: " + id));
            long inUse = kpiMasterRepository.countByDataSource(source.getSourceName());
            if (inUse > 0) {
                log.warn("Cannot delete KPI data source {}. In use by {} KPI masters", id, inUse);
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting KPI data source: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

