package com.example.kpi.controller;

import com.example.kpi.entity.KpiCategory;
import com.example.kpi.repository.KpiCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for KPI Category management (admin only)
 */
@RestController
@RequestMapping("/api/kpi/categories")
@RequiredArgsConstructor
@Slf4j
public class KpiCategoryController {

    private final KpiCategoryRepository repository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<KpiCategory>> findAll() {
        log.debug("Fetching all KPI categories");
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<KpiCategory>> findActive() {
        log.debug("Fetching active KPI categories");
        return ResponseEntity.ok(repository.findByActiveOrderByDisplayOrder(true));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiCategory> findById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiCategory> create(@RequestBody KpiCategory category) {
        log.info("Creating KPI category: {}", category.getCategoryCode());
        try {
            if (category.getCategoryName() == null || category.getCategoryCode() == null) {
                return ResponseEntity.badRequest().build();
            }
            if (category.getDisplayOrder() == null) {
                category.setDisplayOrder(999);
            }
            if (category.getActive() == null) {
                category.setActive(true);
            }
            KpiCategory saved = repository.save(category);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            log.error("Error creating KPI category", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KpiCategory> update(@PathVariable Long id, @RequestBody KpiCategory request) {
        log.info("Updating KPI category: {}", id);
        try {
            KpiCategory existing = repository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("KPI category not found: " + id));
            if (request.getCategoryName() != null) existing.setCategoryName(request.getCategoryName());
            if (request.getDescription() != null) existing.setDescription(request.getDescription());
            if (request.getDisplayOrder() != null) existing.setDisplayOrder(request.getDisplayOrder());
            if (request.getActive() != null) existing.setActive(request.getActive());
            return ResponseEntity.ok(repository.save(existing));
        } catch (Exception e) {
            log.error("Error updating KPI category: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting KPI category: {}", id);
        try {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting KPI category: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

